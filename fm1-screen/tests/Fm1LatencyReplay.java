// SPDX-License-Identifier: GPL-3.0-only
import com.josxtm.fm1screen.protocol.FrameBuffer;
import com.josxtm.fm1screen.protocol.MirrorEvent;
import com.josxtm.fm1screen.protocol.MirrorPackets;
import com.josxtm.fm1screen.protocol.ScreenFrame;
import com.josxtm.fm1screen.protocol.SysExFramer;
import com.josxtm.fm1screen.protocol.Wire;
import com.josxtm.fm1screen.transport.MidiIngress;
import com.josxtm.fm1screen.transport.MirrorListener;
import com.josxtm.fm1screen.transport.MirrorScope;
import com.josxtm.fm1screen.transport.MirrorSession;
import com.josxtm.fm1screen.transport.SessionClock;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Proxy;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import kotlin.Unit;

class Fm1LatencyReplay {
    private static final int DURATION_MS = 30_000;
    private static final int SIDE = 240;
    private static final com.sun.management.ThreadMXBean THREAD = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
    private static volatile int check;
    private static short[] image = new short[SIDE * SIDE];
    private static int publications;
    private static long bytesCopied;
    private static long presentationStart = -1;
    private static long presentationEnd = -1;
    private static long pendingInput = -1;
    private static long skipped;
    private static final List<Long> LATEST = new ArrayList<>();
    private static final List<Long> OLDEST = new ArrayList<>();
    private static final List<Long> ALL = new ArrayList<>();

    private static class Clock implements SessionClock {
        long ms = 1_000;
        public long elapsedMillis() { return ms; }
        public long nanoTime() { return ms * 1_000_000; }
    }

    // Synthetic strips. Four changing rows each 13 ms, with F8 between two callback fragments.
    private static byte[] row(int y, int x, int count, short value) {
        return new byte[] {(byte)0xf0, 0x7d, 0x46, 0x53, 1, (byte)(y & 127), (byte)(y >> 7),
            (byte)(x & 127), (byte)(x >> 7), (byte)count, (byte)(value & 127),
            (byte)((value >>> 7) & 127), (byte)((value & 65535) >>> 14), (byte)0xf7};
    }

    private static long gc(int which) {
        return ManagementFactory.getGarbageCollectorMXBeans().stream()
            .mapToLong(g -> which == 0 ? g.getCollectionCount() : g.getCollectionTime())
            .filter(n -> n >= 0).sum();
    }

    private static double percentile(List<Long> values, double q) {
        var sorted = values.stream().mapToLong(Long::longValue).sorted().toArray();
        return sorted[(int)Math.ceil(sorted.length*q)-1]/1_000_000.0;
    }

    private static void replay(String mode, boolean output) throws Exception {
        var clock = new Clock();
        var expected = new short[SIDE * SIDE];
        var unseen = new ArrayDeque<Long>();
        Arrays.fill(image,(short)0);
        publications = 0; bytesCopied = 0; skipped=0;
        LATEST.clear(); OLDEST.clear(); ALL.clear(); presentationStart = -1; presentationEnd = -1; pendingInput = -1;
        var optimized = !mode.equals("017");
        var handoff = optimized ? new Handoff() : null;
        var listener = (MirrorListener)Proxy.newProxyInstance(MirrorListener.class.getClassLoader(),
            new Class<?>[]{MirrorListener.class}, (proxy, method, params) -> {
                if (method.getName().equals("frame") && params[0] != null) {
                    ((ScreenFrame)params[0]).copyRgb565To(image);
                    bytesCopied += SIDE * SIDE * 4; // snapshot + host transfer, excludes Android's Bitmap upload
                    publications++;
                    if (presentationEnd >= 0) skipped++;
                    if (presentationStart < 0) presentationStart = pendingInput;
                    pendingInput=-1;
                    presentationEnd = (long)params[1];
                }
                if (optimized && method.getName().equals("frameBuffer") && params[0] != null) {
                    handoff.offer.invoke(handoff.mailbox, params[0], params[1]);
                    publications++;
                }
                return null;
            });
        var session = new MirrorSession(clock, b -> true, listener);
        session.configure(Wire.BIG1, false, false, false, MirrorScope.FELUCCA);
        Method present = null;
        if (optimized) {
            var profile = Class.forName("com.josxtm.fm1screen.performance.PresentationMode");
            session.getClass().getMethod("setPresentationMode", profile).invoke(session, profile.getField(mode).get(null));
            present = session.getClass().getMethod("publishDueFrame");
        }
        var ingress = new MidiIngress(65536, 512);
        var id = Thread.currentThread().getId();
        var allocAt = THREAD.getThreadAllocatedBytes(id);
        var cpuAt = THREAD.getCurrentThreadCpuTime();
        var gcAt = gc(0);
        var gcTimeAt = gc(1);
        byte[] f8 = {(byte)0xf8};
        for (int t=0; t<DURATION_MS+120; t++) {
            clock.ms = 1000 + t;
            if (t < DURATION_MS && t % 13 == 7) {
                if (!optimized && pendingInput < 0) pendingInput = clock.nanoTime();
                int x = (t / 13 * 3) % 209;
                var color = (short)(t / 13 + 101);
                for (int i=0;i<4;i++) {
                    int y = (t / 13 + i) % SIDE;
                    var packet=row(y,x,32,color);
                    var received=clock.nanoTime();
                    unseen.addLast(received);
                    ingress.offer(packet,0,6,received);
                    ingress.offer(f8,0,1,received);
                    ingress.offer(packet,6,packet.length-6,received);
                    Arrays.fill(expected, y*SIDE+x, y*SIDE+x+32, color);
                }
                for (MidiIngress.Entry next; (next=ingress.take())!=null;) {
                    if (next instanceof MidiIngress.Entry.Chunk c) session.receive(c.getBytes(),c.getReceivedNanos());
                    else throw new IllegalStateException("RX overflow");
                }
            }
            if (t%16==0) session.tick();
            if (optimized) {
                present.invoke(session);
                if (t % 16 == 0) {
                    var frame = handoff.take.invoke(handoff.mailbox, image);
                    if (frame != null) {
                        var stamps = handoff.timing.invoke(frame);
                        presentationStart = (long)handoff.first.invoke(stamps);
                        presentationEnd = (long)handoff.last.invoke(stamps);
                    }
                }
            }
            if (t%16==0 && presentationEnd>=0) {
                LATEST.add(clock.nanoTime()-presentationEnd);
                while (!unseen.isEmpty() && unseen.getFirst() <= presentationEnd) {
                    ALL.add(clock.nanoTime() - unseen.removeFirst());
                }
                if (presentationStart>=0) OLDEST.add(clock.nanoTime()-presentationStart);
                presentationStart=-1; presentationEnd=-1;
            }
        }
        var cpu = (THREAD.getCurrentThreadCpuTime()-cpuAt)/1_000_000.0;
        var alloc = THREAD.getThreadAllocatedBytes(id)-allocAt;
        long collections = gc(0)-gcAt, gcMs = gc(1)-gcTimeAt;
        if (optimized) {
            bytesCopied = (long)handoff.copied.invoke(handoff.mailbox);
            skipped = (long)handoff.omitted.invoke(handoff.mailbox);
        }
        if (!unseen.isEmpty()) throw new IllegalStateException("No se presentaron todos los bloques");
        if (!Arrays.equals(expected,image)) throw new IllegalStateException("Partial updates failed integrity check");
        check = Arrays.hashCode(image);
        if (output) System.out.printf(java.util.Locale.ROOT,
            "%s,%.2f,%d,%d,%d,%d,%d,%d,%d,%.3f,%.3f,%.3f,%s,%.3f,%.3f,%.3f,%08X%n",
            mode,cpu,alloc,collections,gcMs,publications,LATEST.size(),skipped,bytesCopied,
            percentile(LATEST,.50),percentile(LATEST,.95),percentile(LATEST,.99),
            OLDEST.isEmpty()?"":String.format(java.util.Locale.ROOT,"%.3f",percentile(OLDEST,.95)),
            percentile(ALL,.50),percentile(ALL,.95),percentile(ALL,.99),check);
    }

    // Resolve the new presentation API once so the same fixture can also compile against build 017.
    private static class Handoff {
        final Object mailbox;
        final Method offer, take, timing, first, last, copied, omitted;

        Handoff() throws Exception {
            var type = Class.forName("com.josxtm.fm1screen.protocol.LatestFrame");
            var frame = Class.forName("com.josxtm.fm1screen.protocol.FrameUpdate");
            var stamp = Class.forName("com.josxtm.fm1screen.protocol.FrameTiming");
            mailbox = type.getConstructor().newInstance();
            offer = type.getMethod("offer", FrameBuffer.class, stamp);
            take = type.getMethod("consume", short[].class);
            timing = frame.getMethod("getTiming");
            first = stamp.getMethod("getFirstReceivedNanos");
            last = stamp.getMethod("getLastReceivedNanos");
            copied = type.getMethod("copiedBytes");
            omitted = type.getMethod("superseded");
        }
    }

    private static void stages() {
        final long[] times = new long[4];
        var buffer = new FrameBuffer();
        var framer = new SysExFramer((s,a,b)->Unit.INSTANCE, m->{
            long began=System.nanoTime();
            var p=(MirrorEvent.Pixels)MirrorPackets.INSTANCE.decode(Wire.BIG1,m);
            long decoded=System.nanoTime();
            buffer.apply(p.getPatch());
            times[2]+=decoded-began;
            times[3]+=System.nanoTime()-decoded;
            return Unit.INSTANCE;
        });
        var inbox=new MidiIngress(65536,512);
        var packet=row(0,3,32,(short)0xF800);
        int iterations=200_000;
        for (int j=0;j<2;j++) {
            Arrays.fill(times,0);
            for (int i=0;i<iterations;i++) {
                long began=System.nanoTime();
                inbox.offer(packet,0,packet.length,began);
                var next=(MidiIngress.Entry.Chunk)inbox.take();
                inbox.take();
                long drained=System.nanoTime();
                framer.feed(next.getBytes(),0,packet.length);
                times[0]+=drained-began;
                times[1]+=System.nanoTime()-drained;
            }
        }
        System.out.printf(java.util.Locale.ROOT,"STAGES ns/row, ingress %.1f, framer(excluding decoded work) %.1f, decode %.1f, apply %.1f%n",
            (double)times[0]/iterations,(double)(times[1]-times[2]-times[3])/iterations,
            (double)times[2]/iterations,(double)times[3]/iterations);
    }

    public static void main(String[] args) throws Exception {
        String mode=args.length==0?"017":args[0];
        System.out.println("mode,cpu_ms,alloc_bytes,gc_count,gc_ms,publishes,draws,skipped,transfer_bytes,latest_p50_ms,latest_p95_ms,latest_p99_ms,oldest_p95_ms,block_p50_ms,block_p95_ms,block_p99_ms,crc");
        for (int i=0;i<8;i++) replay(mode,i>=3);
        stages();
    }
}

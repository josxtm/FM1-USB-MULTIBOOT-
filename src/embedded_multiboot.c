/* SPDX-License-Identifier: MIT */
#include "embedded_multiboot.h"
static void mb_copy(void *d,const void*s,unsigned long n){unsigned char*dd=d;const unsigned char*ss=s;while(n--)*dd++=*ss++;}
static void mb_zero(void*d,unsigned long n){unsigned char*dd=d;while(n--)*dd++=0;}
static int mb_equal(const void*a,const void*b,unsigned long n){const unsigned char*x=a,*y=b;while(n--)if(*x++!=*y++)return 0;return 1;}
static unsigned long lenof(const char*s){unsigned long n=0;while(s[n])n++;return n;}
static void mb_shift_left(char*s,unsigned long at,unsigned long len){for(unsigned long i=at;i+1<len;i++)s[i]=s[i+1];}
static int same(const char *a,const char*b){while(*a&&*a==*b){a++;b++;}return *a==*b;}
static int fwpath(const char *name,char out[20]){unsigned long n=lenof(name);if(n<5||n>12||name[0]=='.')return 0;for(unsigned long j=0;j<n;j++)if(name[j]=='/'||name[j]=='\\'||(name[j]=='.'&&j&&name[j-1]=='.'))return 0;unsigned long i=0;const char p[]="FM1/FW/";while(p[i]){out[i]=p[i];i++;}for(unsigned long j=0;j<n;j++)out[i++]=name[j];out[i]=0;return 1;}
static void copystr(char *d,unsigned long cap,const char*s){unsigned long i=0;while(i+1<cap&&s[i]){d[i]=s[i];i++;}d[i]=0;}
static char *findc(char*s,char c){while(*s&&*s!=c)s++;return *s?s:0;}
static int number(const char*s,uint32_t*out){uint32_t v=0;if(!*s)return 0;while(*s){if(*s<'0'||*s>'9'||v>(MB_MAX_IMAGE-(uint32_t)(*s-'0'))/10u)return 0;v=v*10u+(uint32_t)(*s-'0');s++;}*out=v;return 1;}
static void fail(struct mb_core *c, const char *s) { c->state = MB_ERROR; copystr(c->error,sizeof c->error,s); }
static int hex64(const char *s) { for (unsigned i=0;i<64;i++) if (!((s[i]>='0'&&s[i]<='9')||(s[i]>='a'&&s[i]<='f'))) return 0; return s[64]==0; }
static int safe_id(const char *s) { unsigned long n=lenof(s); if(!n||n>MB_ID_MAX)return 0; for(unsigned long i=0;i<n;i++) if(!((s[i]>='A'&&s[i]<='Z')||(s[i]>='0'&&s[i]<='9')||s[i]=='_'))return 0; return 1; }
static int field(const char *s, unsigned long max) { unsigned long n=lenof(s); if(!n||n>max)return 0; for(unsigned long i=0;i<n;i++) { unsigned char ch=(unsigned char)s[i]; if(ch<32||ch>126||ch=='|')return 0; } return 1; }
static int label_field(const char *s) { unsigned long n=lenof(s); if(!field(s,MB_LABEL_MAX))return 0; for(unsigned long i=0;i<n;i++) if(!((s[i]>='A'&&s[i]<='Z')||(s[i]>='a'&&s[i]<='z')||(s[i]>='0'&&s[i]<='9')||s[i]==' '||s[i]=='.'||s[i]=='_'||s[i]=='-'))return 0; return 1; }
static int version_field(const char *s) { unsigned long n=lenof(s); if(!field(s,MB_VERSION_MAX))return 0; for(unsigned long i=0;i<n;i++) if(!((s[i]>='A'&&s[i]<='Z')||(s[i]>='a'&&s[i]<='z')||(s[i]>='0'&&s[i]<='9')||s[i]=='.'||s[i]=='_'||s[i]=='-'))return 0; return 1; }
static int parse_line(struct mb_catalog *cat, char *line) {
    char *p[7], *q=line; unsigned n=0; for(;;) { if(n==7)return 0; p[n++]=q; q=findc(q,'|'); if(!q)break; *q++=0; }
    if(n!=7 || !safe_id(p[0]) || !label_field(p[1]) || !version_field(p[2]) ||
       !field(p[3],12) || !field(p[4],10) || !hex64(p[5]) || !same(p[6],"UNVERIFIED")) return 0;
    struct mb_entry *e=&cat->entries[cat->count]; unsigned long id=lenof(p[0]); char expected[13]; uint32_t size;
    copystr(expected,sizeof expected,p[0]); copystr(expected+id,sizeof expected-id,".BIN");
    if(!same(p[3],expected)){copystr(expected+id,sizeof expected-id,".FWS");if(!same(p[3],expected))return 0;}
    copystr(e->id,sizeof e->id,p[0]);
    if(!number(p[4],&size)||!size)return 0;
    for(uint32_t i=0;i<cat->count;i++) if(same(cat->entries[i].id,e->id))return 0;
    if(id>MB_ID_MAX)return 0;
    copystr(e->label,sizeof e->label,p[1]); copystr(e->version,sizeof e->version,p[2]);
    copystr(e->file,sizeof e->file,p[3]); copystr(e->sha256,sizeof e->sha256,p[5]); e->size=size; e->metadata_valid=1; cat->count++; return 1;
}
static int parse_catalog(struct mb_catalog *cat, char *buf, unsigned long len) {
    for(unsigned long i=0;i<len;) { if(buf[i]=='\r'){if(i+1>=len||buf[i+1]!='\n')return 0;mb_shift_left((char *)buf,i,len);--len;continue;}++i; }
    static const char h1[]="FM1CAT|1\n", h2[]="ID|LABEL|VERSION|FILE|SIZE|SHA256|COMPAT\n";
    if(len<sizeof h1+sizeof h2-2 || !mb_equal(buf,h1,sizeof h1-1) || !mb_equal(buf+sizeof h1-1,h2,sizeof h2-1))return 0;
    unsigned long pos=sizeof h1+sizeof h2-2; while(pos<len) { unsigned long end=pos; while(end<len&&buf[end]!='\n')end++; if(end==pos||end==len||end-pos>160)return 0; buf[end]=0; if(cat->count>=MB_MAX_ENTRIES||!parse_line(cat,buf+pos))return 0; pos=end+1; }
    return 1;
}
void mb_init(struct mb_core *c, struct mb_storage s, struct mb_renderer r) { if(!c)return; mb_zero(c,sizeof *c); c->state=MB_BOOT; c->storage=s; c->renderer=r; }
enum mb_io_result mb_load_catalog(struct mb_core *c) {
    if(!c)return MB_IO_ERROR;
    c->state=MB_LOADING_CATALOG; c->catalog.count=0; c->selected=0; c->error[0]=0; static uint8_t data[MB_MAX_CATALOG+1]; uint32_t got=0;
    if(!c->storage.read){c->state=MB_NO_STORAGE;return MB_IO_MISSING;}
    for(;;){
        uint32_t chunk=0; enum mb_io_result r=c->storage.read(c->storage.context,"FM1/INDEX.CSV",got,data+got,MB_MAX_CATALOG-got,&chunk);
        if(r==MB_IO_EOF) { if(chunk) { fail(c,"INDEX.CSV EOF with bytes"); return MB_IO_ERROR; } break; }
        if(r!=MB_IO_OK){c->state=(r==MB_IO_MISSING||r==MB_IO_DISCONNECTED)?MB_NO_STORAGE:MB_ERROR;copystr(c->error,sizeof c->error,"INDEX.CSV read failed");return r;}
        if(!chunk||chunk>MB_MAX_CATALOG-got){fail(c,"INDEX.CSV read bounds");return MB_IO_ERROR;}
        got+=chunk;
        if(got==MB_MAX_CATALOG){uint8_t one;uint32_t extra=0;r=c->storage.read(c->storage.context,"FM1/INDEX.CSV",got,&one,1,&extra);if(r==MB_IO_EOF)break;fail(c,"INDEX.CSV exceeds limit");return MB_IO_ERROR;}
    }
    if(!parse_catalog(&c->catalog,(char*)data,got)){fail(c,"INDEX.CSV inválido");return MB_IO_ERROR;}
    c->state=c->catalog.count?MB_BROWSE:MB_EMPTY; return MB_IO_OK;
}
/* Small self-contained SHA-256 implementation for read-only verification. */
struct sha { uint32_t h[8]; mb_u64 n; uint8_t b[64]; unsigned used; };
static uint32_t rr(uint32_t x,unsigned n){return x>>n|x<<(32-n);} static const uint32_t K[64]={0x428a2f98,0x71374491,0xb5c0fbcf,0xe9b5dba5,0x3956c25b,0x59f111f1,0x923f82a4,0xab1c5ed5,0xd807aa98,0x12835b01,0x243185be,0x550c7dc3,0x72be5d74,0x80deb1fe,0x9bdc06a7,0xc19bf174,0xe49b69c1,0xefbe4786,0x0fc19dc6,0x240ca1cc,0x2de92c6f,0x4a7484aa,0x5cb0a9dc,0x76f988da,0x983e5152,0xa831c66d,0xb00327c8,0xbf597fc7,0xc6e00bf3,0xd5a79147,0x06ca6351,0x14292967,0x27b70a85,0x2e1b2138,0x4d2c6dfc,0x53380d13,0x650a7354,0x766a0abb,0x81c2c92e,0x92722c85,0xa2bfe8a1,0xa81a664b,0xc24b8b70,0xc76c51a3,0xd192e819,0xd6990624,0xf40e3585,0x106aa070,0x19a4c116,0x1e376c08,0x2748774c,0x34b0bcb5,0x391c0cb3,0x4ed8aa4a,0x5b9cca4f,0x682e6ff3,0x748f82ee,0x78a5636f,0x84c87814,0x8cc70208,0x90befffa,0xa4506ceb,0xbef9a3f7,0xc67178f2};
static void shinit(struct sha*s){static const uint32_t h[]={0x6a09e667,0xbb67ae85,0x3c6ef372,0xa54ff53a,0x510e527f,0x9b05688c,0x1f83d9ab,0x5be0cd19};mb_copy(s->h,h,sizeof h);s->n=s->used=0;}
static void shblock(struct sha*s,const uint8_t*b){uint32_t w[64],a,bv,c,d,e,f,g,h;for(unsigned i=0;i<16;i++)w[i]=(uint32_t)b[4*i]<<24|(uint32_t)b[4*i+1]<<16|(uint32_t)b[4*i+2]<<8|b[4*i+3];for(unsigned i=16;i<64;i++){uint32_t x=rr(w[i-15],7)^rr(w[i-15],18)^(w[i-15]>>3),y=rr(w[i-2],17)^rr(w[i-2],19)^(w[i-2]>>10);w[i]=w[i-16]+x+w[i-7]+y;}a=s->h[0];bv=s->h[1];c=s->h[2];d=s->h[3];e=s->h[4];f=s->h[5];g=s->h[6];h=s->h[7];for(unsigned i=0;i<64;i++){uint32_t S1=rr(e,6)^rr(e,11)^rr(e,25),ch=(e&f)^((~e)&g),t1=h+S1+ch+K[i]+w[i],S0=rr(a,2)^rr(a,13)^rr(a,22),maj=(a&bv)^(a&c)^(bv&c),t2=S0+maj;h=g;g=f;f=e;e=d+t1;d=c;c=bv;bv=a;a=t1+t2;}s->h[0]+=a;s->h[1]+=bv;s->h[2]+=c;s->h[3]+=d;s->h[4]+=e;s->h[5]+=f;s->h[6]+=g;s->h[7]+=h;}
static void shadd(struct sha*s,const uint8_t*b,unsigned long n){s->n+=n;while(n){unsigned long k=64-s->used;if(k>n)k=n;mb_copy(s->b+s->used,b,k);s->used+=(unsigned)k;b+=k;n-=k;if(s->used==64){shblock(s,s->b);s->used=0;}}}
static void shfinal(struct sha*s,char out[65]){static const char x[]="0123456789abcdef";mb_u64 bits=s->n*8;uint8_t z[128]={0x80};unsigned long pad=s->used<56?56-s->used:120-s->used;shadd(s,z,pad);uint8_t l[8];for(int i=0;i<8;i++)l[7-i]=(uint8_t)(bits>>(8*i));shadd(s,l,8);for(unsigned i=0;i<8;i++)for(unsigned j=0;j<8;j++)out[8*i+j]=x[(s->h[i]>>(28-4*j))&15];out[64]=0;}
enum mb_io_result mb_verify_selected(struct mb_core *c){if(!c||!c->storage.read||c->selected>=c->catalog.count)return MB_IO_ERROR;struct mb_entry*e=&c->catalog.entries[c->selected];char path[20];if(!fwpath(e->file,path)){c->state=MB_INCOMPATIBLE;return MB_IO_ERROR;}c->state=MB_VERIFY;e->file_present=0;e->integrity_verified=0;uint8_t b[512];uint32_t off=0,n=0;struct sha s;shinit(&s);while(off<e->size){n=0;enum mb_io_result r=c->storage.read(c->storage.context,path,off,b,sizeof b,&n);if(r==MB_IO_EOF){c->state=MB_INCOMPATIBLE;return MB_IO_ERROR;}if(r!=MB_IO_OK){c->state=(r==MB_IO_DISCONNECTED)?MB_NO_STORAGE:(r==MB_IO_TIMEOUT?MB_ERROR:MB_INCOMPATIBLE);return r;}if(!n||n>sizeof b){c->state=MB_ERROR;return MB_IO_ERROR;}if(n>e->size-off){c->state=MB_INCOMPATIBLE;return MB_IO_ERROR;}shadd(&s,b,n);off+=n;}n=0;enum mb_io_result end=c->storage.read(c->storage.context,path,off,b,1,&n);if(end==MB_IO_OK&&n){c->state=MB_INCOMPATIBLE;return MB_IO_ERROR;}if(end==MB_IO_EOF&&n){c->state=MB_INCOMPATIBLE;return MB_IO_ERROR;}if(end!=MB_IO_EOF){c->state=MB_ERROR;return end;}char got[65];shfinal(&s,got);e->file_present=1;if(same(got,e->sha256)){e->integrity_verified=1;c->state=MB_READY_FOR_SIMULATION;}else c->state=MB_INCOMPATIBLE;return MB_IO_OK;}
void mb_event(struct mb_core*c,enum mb_event e){if(!c||!c->catalog.count)return;if(e==MB_EVENT_REC||e==MB_EVENT_CANCEL){if(c->state==MB_INSPECT||c->state==MB_INCOMPATIBLE||c->state==MB_READY_FOR_SIMULATION)c->state=MB_BROWSE;return;}if(c->state==MB_BROWSE||c->state==MB_INSPECT||c->state==MB_READY_FOR_SIMULATION||c->state==MB_INCOMPATIBLE){if(e==MB_EVENT_PREVIOUS){c->selected=(c->selected+c->catalog.count-1)%c->catalog.count;c->state=MB_BROWSE;}else if(e==MB_EVENT_NEXT){c->selected=(c->selected+1)%c->catalog.count;c->state=MB_BROWSE;}else if(e==MB_EVENT_PLAY)c->state=MB_INSPECT;}}
const struct mb_entry*mb_selected(const struct mb_core*c){return c->selected<c->catalog.count?&c->catalog.entries[c->selected]:0;}
enum mb_launch_result mb_prepare_launch(const struct mb_core*c){(void)c;return MB_LAUNCH_BLOCKED;}
void mb_render(const struct mb_core*c){if(!c||!c->renderer.begin)return;c->renderer.begin(c->renderer.context);if(c->catalog.count&&c->selected<c->catalog.count){uint32_t i=c->selected;const struct mb_entry*e=&c->catalog.entries[i];if(c->renderer.card&&c->catalog.count>=3)c->renderer.card(c->renderer.context,&c->catalog.entries[(i+c->catalog.count-1)%c->catalog.count],-1,0);if(c->renderer.card)c->renderer.card(c->renderer.context,e,0,1);if(c->renderer.card&&c->catalog.count>=2)c->renderer.card(c->renderer.context,&c->catalog.entries[(i+1)%c->catalog.count],1,0);}if(c->renderer.text)c->renderer.text(c->renderer.context,mb_state_name(c->state));if(c->renderer.end)c->renderer.end(c->renderer.context);}
const char*mb_state_name(enum mb_state s){static const char*n[]={"BOOT","NO_STORAGE","LOADING_CATALOG","EMPTY","BROWSE","INSPECT","VERIFY","READY_FOR_SIMULATION","INCOMPATIBLE","ERROR","RECOVERY"};return s<sizeof(n)/sizeof(n[0])?n[s]:"ERROR";}

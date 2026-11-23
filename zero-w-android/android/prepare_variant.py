"""Materialize the Zero variant over Android sources at e401bc4788547f4141fa50df13de37db7523736f."""
from pathlib import Path

root = Path("android/app")
src = root / "src/manager/java/com/josxtm/fm1ramloader"

def edit(file, old, new):
    data = file.read_text()
    if data.count(old) != 1:
        raise RuntimeError(f"Unexpected upstream content in {file}")
    file.write_text(data.replace(old, new))

edit(root / "build.gradle", "'com.josxtm.fm1managerusb'", "'com.josxtm.fm1managerusb.zero'")
edit(root / "src/manager/AndroidManifest.xml", '@string/manager_name', 'FM1 Manager · Zero W')
edit(src / "ManagerActivity.kt", 'Elegir carpeta FM1', 'Preparar tarjeta SD')
edit(src / "CardLibrary.kt", 'val flatPath get() = "FW/', 'val flatPath get() = if (extension == "FWS") "FIRMWARES/${sha.lowercase()}.fwsc" else "FW/')
store = src / "SafCardStore.kt"
edit(store, '    private fun parent(', '''    fun prepareZeroFolder() {
        val folder = root.findFile("FIRMWARES") ?: root.createDirectory("FIRMWARES")
        require(folder?.isDirectory == true && folder.canWrite()) { "Carpeta de firmwares inaccesible" }
    }

    private fun parent(''')
edit(store, '    private fun parent(', '''    init {
        val mark = root.findFile("ZERO.ID")
        val valid = mark != null && mark.isFile && mark.length() in 1..64 &&
            provider.openInputStream(mark.uri)?.use { it.bounded(64).decodeToString().trim() } == "FM1DATA-V1"
        require(valid) { "Selecciona FM1 en la partición FM1DATA preparada para la Zero" }
    }

    private fun parent(''')
edit(store, 'require(parts.all', '''if (parts.first() == "FIRMWARES") {
            require(parts.size == 2 && parts.last().matches(Regex("[a-f0-9]{64}[.]fwsc")))
        } else require(parts.all''')
edit(src / "ManagerModel.kt", 'SafCardStore.persist(app, uri)', 'store.prepareZeroFolder()\n        SafCardStore.persist(app, uri)')

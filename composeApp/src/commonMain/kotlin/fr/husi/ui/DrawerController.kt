package fr.husi.ui

class DrawerController(
    private val onDrawerClick: () -> Unit,
) {
    fun toggle() {
        onDrawerClick()
    }
}

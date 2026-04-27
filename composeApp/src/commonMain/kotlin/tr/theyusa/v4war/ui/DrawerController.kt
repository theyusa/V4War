package tr.theyusa.v4war.ui

class DrawerController(
    private val onDrawerClick: () -> Unit,
) {
    fun toggle() {
        onDrawerClick()
    }
}

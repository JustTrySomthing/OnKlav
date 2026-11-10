package app.onklav.home
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeTestRule

/** Paused offscreen windows need an explicit layout after each scroll step. */
internal fun SemanticsNodeInteraction.reveal(compose:ComposeTestRule,pump:()->Unit):SemanticsNodeInteraction {
    repeat(16) {
        pump()
        if(runCatching{assertIsDisplayed()}.isSuccess)return this
        val target=fetchSemanticsNode();var ancestor=target.parent;var moved=false
        while(ancestor!=null) {
            val action=ancestor.config.getOrNull(SemanticsActions.ScrollBy)?.action
            if(action!=null) {
                val viewport=ancestor.boundsInRoot;val position=target.positionInRoot
                val dx=when {position.x<viewport.left->position.x-viewport.left;position.x+target.size.width>viewport.right->position.x+target.size.width-viewport.right;else->0f}
                val dy=when {position.y<viewport.top->position.y-viewport.top;position.y+target.size.height>viewport.bottom->position.y+target.size.height-viewport.bottom;else->0f}
                if(dx!=0f||dy!=0f){compose.runOnUiThread{action(dx,dy)};moved=true;break}
            }
            ancestor=ancestor.parent
        }
        check(moved){"Offscreen control has no reachable scroll container"}
    }
    assertIsDisplayed();return this
}

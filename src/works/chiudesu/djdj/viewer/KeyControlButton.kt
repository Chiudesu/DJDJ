package works.chiudesu.djdj.viewer

import javafx.scene.Node
import javafx.scene.control.ToggleButton
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import javafx.scene.input.MouseEvent

//isSelectOnlyPressing==trueならマウスまたはキー押している間だけselected
//MousePress,Release、KeyPress,ReleaseイベントはFilterによって発生しない
class KeyControlButton :ToggleButton(){

    var isSelectOnlyPressing :Boolean = false
    
    var selectedText = "ON"
        set(value){
            field = value
            if(isSelected)text = value
        }

    var unselectedText = "OFF"
        set(value){
            field = value
            if(isSelected)text = value
        }

    var onSelect :()->Unit = {}
    var onDeselect :()->Unit = {}


    init{
        text = unselectedText

        this.addEventFilter(KeyEvent.KEY_PRESSED, {
            if(it.code == KeyCode.SPACE){
                it.consume()

                isSelected =
                        if(isSelectOnlyPressing)true
                        else !isSelected
                if(isSelected)onSelect()
                changeText()
            }
        })
        this.addEventFilter(KeyEvent.KEY_RELEASED, {
            if(it.code == KeyCode.SPACE){
                it.consume()

                if(isSelectOnlyPressing)isSelected = false
                if(!isSelected)onDeselect()
                changeText()
            }
        })
        this.addEventFilter(MouseEvent.MOUSE_PRESSED, {
            it.consume()

            isSelected =
                    if(isSelectOnlyPressing)true
                    else !isSelected
            if(isSelected)onSelect()
            changeText()
        })
        this.addEventFilter(MouseEvent.MOUSE_RELEASED, {
            it.consume()

            if(isSelectOnlyPressing)isSelected = false
            if(!isSelected)onDeselect()
            changeText()
        })
    }

    fun attach(key :KeyCode, node :Node){
        node.addEventFilter(KeyEvent.KEY_PRESSED, {
            if(it.code == key){
                it.consume()

                isSelected =
                        if(isSelectOnlyPressing)true
                        else !isSelected
                if(isSelected)onSelect()
                changeText()
            }
        })
        node.addEventFilter(KeyEvent.KEY_RELEASED, {
            if(it.code == key){
                it.consume()

                if(isSelectOnlyPressing)isSelected = false
                if(!isSelected)onDeselect()
                changeText()
            }
        })
    }

    private fun changeText(){
        if(isSelected) text = selectedText
        else text = unselectedText
    }
}
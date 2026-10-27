package com.onlygoodthings.app.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.VisualTransformation

/** Keep native selection/composition local while the parent receives synchronous text edits. */
@Composable
fun OgtOutlinedTextField(
    value:String,
    onValueChange:(String)->Unit,
    modifier:Modifier=Modifier,
    enabled:Boolean=true,
    readOnly:Boolean=false,
    textStyle:TextStyle=LocalTextStyle.current,
    label:(@Composable ()->Unit)?=null,
    placeholder:(@Composable ()->Unit)?=null,
    leadingIcon:(@Composable ()->Unit)?=null,
    trailingIcon:(@Composable ()->Unit)?=null,
    prefix:(@Composable ()->Unit)?=null,
    suffix:(@Composable ()->Unit)?=null,
    supportingText:(@Composable ()->Unit)?=null,
    isError:Boolean=false,
    visualTransformation:VisualTransformation=VisualTransformation.None,
    keyboardOptions:KeyboardOptions=KeyboardOptions(capitalization=KeyboardCapitalization.Sentences),
    keyboardActions:KeyboardActions=KeyboardActions.Default,
    singleLine:Boolean=false,
    maxLines:Int=if(singleLine) 1 else Int.MAX_VALUE,
    minLines:Int=1,
    interactionSource:MutableInteractionSource?=null,
    shape:Shape=OutlinedTextFieldDefaults.shape,
    colors:TextFieldColors=OutlinedTextFieldDefaults.colors(),
) {
    var editor by remember { mutableStateOf(TextFieldValue(value)) }
    var lastEmittedText by remember { mutableStateOf(value) }
    // Only external edits (reset, loaded value, explicit formatting) replace the native buffer.
    // Ordinary recomposition must not rebuild its selection or marked-text composition.
    SideEffect {
        if(value!=lastEmittedText) {
            editor=editor.copy(text=value,selection=TextRange(
                editor.selection.start.coerceIn(0,value.length),
                editor.selection.end.coerceIn(0,value.length)),composition=null)
            lastEmittedText=value
        }
    }
    OutlinedTextField(editor,{ next ->
        editor=next
        if(next.text!=lastEmittedText) {
            lastEmittedText=next.text
            onValueChange(next.text)
        }
    },modifier,enabled,readOnly,textStyle,label,placeholder,
        leadingIcon,trailingIcon,prefix,suffix,supportingText,isError,visualTransformation,
        keyboardOptions,keyboardActions,singleLine,maxLines,minLines,interactionSource,shape,colors)
}

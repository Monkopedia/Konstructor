/*
 * Copyright 2020 Jason Monk
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.monkopedia.konstructor.frontend

import kotlinx.html.TEXTAREA
import org.w3c.dom.Element
import org.w3c.dom.events.Event
import org.w3c.dom.events.EventTarget

// external object CodeMirror {
//     @JsName("fromTextArea")
//     fun fromTextArea(element: dynamic, config: dynamic)
// }
// @JsName("fromTextArea")
// external fun fromTextArea(element: dynamic, config: dynamic): Any
//
// @JsName("commands")
// external val commands: dynamic

@JsModule("codemirror")
@JsNonModule
external class CodeMirror : EventTarget {
    class Doc : EventTarget {
        /**
         Gets the (outer) mode object for the editor. Note that this is distinct from getOption("mode"), which gives you the mode specification, rather than the resolved, instantiated mode object.
         */
        fun getMode(): Any

        /**
         Get the current editor content. You can pass it an optional argument to specify the String to be used to separate lines (defaults to "\n").
         */
        fun getValue(separator: String? = definedExternally): String

        /**
         Set the editor content.
         */
        fun setValue(content: String)

        /**
         Get the text between the given points in the editor, which should be Location objects. An optional third argument can be given to indicate the line separator String to use (defaults to "\n").
         */
        fun getRange(from: Location, to: Location, separator: String? = definedExternally): String

        /**
         Replace the part of the document between from and to with the given String. from and to must be Location objects. to can be left off to simply insert the String at position from. When origin is given, it will be passed on to "change" events, and its first letter will be used to determine whether this change can be merged with previous history events, in the way described for selection origins.
         */
        fun replaceRange(
            replacement: String,
            from: Location,
            to: Location,
            origin: String? = definedExternally
        )

        /**
         Get the content of line n.
         */
        fun getLine(n: Int): String

        /**
         Get the Number of lines in the editor.
         */
        fun lineCount(): Int

        /**
         Get the Number of first line in the editor. This will usually be zero but for linked sub-views, or documents instantiated with a non-zero first line, it might return other values.
         */
        fun firstLine(): Int

        /**
         Get the Number of last line in the editor. This will usually be lineCount() - 1, but for linked sub-views, it might return other values.
         */
        fun lastLine(): Int

        interface LineHandle

        /**
         Fetches the line handle for the given line Number.
         */
        fun getLineHandle(num: Int): LineHandle

        /**
         Given a line handle, returns the current position of that line (or null when it is no longer in the document).
         */
        fun getLineNumber(handle: LineHandle): Int

        /**
         eachLine(start: Int, end: Int, f: (line: LineHandle))
         */
        fun eachLine(f: (line: LineHandle) -> Unit)

        /**
         fun Set the editor content as 'clean', a flag that it will retain until it is edited, and which will be set again when such an edit is undone again. Useful to track whether the content needs to be saved. This function is deprecated in favor of changeGeneration, which allows multiple subsystems to track different notions of cleanness without interfering.
         */
        fun markClean()

        /**
         Returns a Number that can later be passed to isClean to test whether any edits were made (and not undone) in the meantime. If closeEvent is true, the current history event will be ‘closed’, meaning it can't be combined with further changes (rapid typing or deleting events are typically combined).
         */
        fun changeGeneration(closeEvent: Boolean? = definedExternally): Int

        /**
         Returns whether the document is currently clean — not modified since initialization or the last call to markClean if no argument is passed, or since the matching call to changeGeneration if a generation value is given.
         */
        fun isClean(generation: Int? = definedExternally): Boolean
        /**
         Cursor and selection methods
         */
        /**
         Get the currently selected code. Optionally pass a line separator to put between the lines in the output. When multiple selections are present, they are concatenated with instances of lineSep in between.
         */
        fun getSelection(lineSep: String? = definedExternally): String

        /**
         Returns an Array containing a String for each selection, representing the content of the selections.
         */
        fun getSelections(lineSep: String? = definedExternally): Array<String>

        /**
         Replace the selection(s) with the given String. By default, the new selection ends up after the inserted text. The optional select argument can be used to change this—passing "around" will cause the new text to be selected, passing "start" will collapse the selection to the start of the inserted text.
         */
        fun replaceSelection(replacement: String, select: String? = definedExternally)

        /**
         The length of the given Array should be the same as the Number of active selections. Replaces the content of the selections with the Strings in the Array. The select argument works the same as in replaceSelection.
         */
        fun replaceSelections(replacements: Array<String>, select: String? = definedExternally)

        /**
         Retrieve one end of the primary selection. start is an optional String indicating which end of the selection to return. It may be "from", "to", "head" (the side of the selection that moves when you press shift+arrow), or "anchor" (the fixed side of the selection). Omitting the argument is the same as passing "head". A Location object will be returned.
         */
        fun getCursor(start: String? = definedExternally): Location

        /**
         Retrieves a list of all current selections. These will always be sorted, and never overlap (overlapping selections are merged). Each object in the Array contains anchor and head properties referring to Location objects.
         */
        interface Selection {
            val anchor: Location
            val head: Location?
        }

        fun listSelections(): Array<Selection>

        /**
         Return true if any text is selected.
         */
        fun somethingSelected(): Boolean

        /**
         Set the cursor position. You can either pass a single Location object, or the line and the character as two separate parameters. Will replace all selections with a single, empty selection at the given position. The supported options are the same as for setSelection.
         */
        fun setCursor(
            pos: Location,
            ch: Number? = definedExternally,
            options: SelectionOptions? = definedExternally
        )

        /**
         Set the cursor position. You can either pass a single Location object, or the line and the character as two separate parameters. Will replace all selections with a single, empty selection at the given position. The supported options are the same as for setSelection.
         */
        fun setCursor(
            pos: Number,
            ch: Number? = definedExternally,
            options: SelectionOptions? = definedExternally
        )

        /**
         Set a single selection range. anchor and head should be Location objects. head defaults to anchor when not given. These options are supported:
         */
        fun setSelection(
            anchor: Location,
            head: Location? = definedExternally,
            options: SelectionOptions? = definedExternally
        )

        /**
         Sets a new set of selections. There must be at least one selection in the given Array. When primary is a Number, it determines which selection is the primary one. When it is not given, the primary index is taken from the previous selection, or set to the last range if the previous selection had less ranges than the new one. Supports the same options as setSelection. head defaults to anchor when not given.
         */
        fun setSelections(
            ranges: Array<Selection>,
            primary: Int? = definedExternally,
            options: SelectionOptions? = definedExternally
        )

        /**
         Adds a new selection to the existing set of selections, and makes it the primary selection.
         */
        fun addSelection(anchor: Location, head: Location? = definedExternally)

        /**
         Similar to setSelection, but will, if shift is held or the extending flag is set, move the head of the selection while leaving the anchor at its current place. to is optional, and can be passed to ensure a region (for example a word or paragraph) will end up selected (in addition to whatever lies between that region and the current anchor). When multiple selections are present, all but the primary selection will be dropped by this method. Supports the same options as setSelection.
         */
        fun extendSelection(
            from: Location,
            to: Location? = definedExternally,
            options: SelectionOptions? = definedExternally
        )

        /**
         An equivalent of extendSelection that acts on all selections at once.
         */
        fun extendSelections(heads: Array<Location>, options: SelectionOptions? = definedExternally)

        /**
         Applies the given function to all existing selections, and calls extendSelections on the result.
         */
        fun extendSelectionsBy(
            f: (range: Selection) -> Location,
            options: SelectionOptions? = definedExternally
        )

        /**
         Sets or clears the 'extending' flag, which acts similar to the shift key, in that it will cause cursor movement and calls to extendSelection to leave the selection anchor in place.
         */
        fun setExtending(value: Boolean)

        /**
         Get the value of the 'extending' flag.
         */
        fun getExtending(): Boolean
        /**
         Document management methods
         */
        /**

         */
        /**
         Retrieve the editor associated with a document. May return null.
         */
        fun getEditor(): CodeMirror

        /**
         Create an identical copy of the given doc. When copyHistory is true, the history will also be copied. Can not be called directly on an editor.
         */
        fun copy(copyHistory: Boolean): Doc

        /**
         Create a new document that's linked to the target document. Linked documents will stay in sync (changes to one are also applied to the other) until unlinked. These are the options that are supported:
         */
        fun linkedDoc(options: LinkOptions): Doc

        /**
         Break the link between two documents. After calling this, changes will no longer propagate between the documents, and, if they had a shared history, the history will become separate.
         */
        fun unlinkDoc(doc: CodeMirror.Doc)

        /**
         */
        fun iterLinkedDocs(
            function: (doc: Doc, sharedHist: Boolean) -> Unit
        )
        /**
         History-related methods
         Will call the given function for all documents linked to the target document. It will be passed two arguments, the linked document and a Boolean indicating whether that document shares history with the target.
         */
        /**
         Undo one edit (if any undo events are stored).
         */
        fun undo()

        /**
         Redo one undone edit.
         */
        fun redo()

        /**
         Undo one edit or selection change.
         */
        fun undoSelection()

        /**
         Redo one undone edit or selection change.
         */
        fun redoSelection()

        /**
         Returns an object with {undo, redo} properties, both of which hold Ints, indicating the amount of stored undo and redo operations.
         */
        interface HistorySize {
            val undo: Int
            val redo: Int
        }

        fun historySize(): HistorySize

        /**
         Clears the editor's undo history.
         */
        fun clearHistory()

        /**
         Get a (JSON-serializable) representation of the undo history.
         */
        fun getHistory(): Any

        /**
         Replace the editor's undo history with the one provided, which must be a value as returned by getHistory. Note that this will have entirely undefined results if the editor content isn't also the same as it was when getHistory was called.
         */
        fun setHistory(history: Any)

        /**
         Text-marking methods
         Can be used to mark a range of text with a specific CSS class name. from and to should be Location objects. The options parameter is optional. When given, it should be an object that may contain the following configuration options:
         */
        fun markText(
            from: Location,
            to: Location,
            options: MarkTextOptions? = definedExternally
        ): TextMarker

        /**
         The method will return an object that represents the marker (with constructor CodeMirror.TextMarker), which exposes three methods: clear(), to remove the mark, find(), which returns a {from, to} object (both holding document positions), indicating the current position of the marked range, or undefined if the marker is no longer in the document, and finally changed(), which you can call if you've done something that might change the size of the marker (for example changing the content of a replacedWith node), and want to cheaply update the display.
         */

        /**
         Inserts a bookmark, a handle that follows the text around it as it is being edited, at the given position. A bookmark has two methods find() and clear(). The first returns the current position of the bookmark, if it is still in the document, and the second explicitly removes the bookmark. The options argument is optional. If given, the following properties are recognized:
         */
        fun setBookmark(pos: Location, options: BookmarkOptions? = definedExternally): TextMarker

        /**
         Returns an Array of all the bookmarks and marked ranges found between the given positions (non-inclusive).
         */
        fun findMarks(from: Location, to: Location): Array<TextMarker>

        /**
         Returns an Array of all the bookmarks and marked ranges present at the given position.
         */
        fun findMarksAt(pos: Location): Array<TextMarker>

        /**
         Returns an Array containing all marked ranges in the document.
         */
        fun getAllMarks(): Array<TextMarker>

        /**
         Returns the preferred line separator String for this document, as per the option by the same name. When that option is null, the String "\n" is returned.
         */
        fun lineSeparator(): String

        /**
         Calculates and returns a Location object for a zero-based index who's value is relative to the start of the editor's text. If the index is out of range of the text then the returned object is clipped to start or end of the text respectively.
         */
        fun posFromIndex(index: Int): Location

        /**
         The reverse of posFromIndex.
         */
        fun indexFromPos(`object`: Location): Int

        /**
         Widget, gutter, and decoration methods
         */
        /**
         Sets the gutter marker for the given gutter (identified by its CSS class, see the gutters option) to the given value. Value can be either null, to clear the marker, or a DOM element, to set it. The DOM element will be shown in the specified gutter next to the specified line.
         */
        fun setGutterMarker(line: LineHandle, gutterID: String, `value`: Element): LineHandle

        /**
         Sets the gutter marker for the given gutter (identified by its CSS class, see the gutters option) to the given value. Value can be either null, to clear the marker, or a DOM element, to set it. The DOM element will be shown in the specified gutter next to the specified line.
         */
        fun setGutterMarker(line: Int, gutterID: String, `value`: Element): LineHandle

        /**
         Remove all gutter markers in the gutter with the given ID.
         */
        fun clearGutter(gutterID: String)

        /**
         * Set a CSS class name for the given line.
         * line can be a Number or a line handle.
         * where determines to which element this class should be applied, can can be one of
         *     "text" (the text element, which lies in front of the selection),
         *     "background" (a background element that will be behind the selection),
         *     "gutter" (the line's gutter space), or
         *     "wrap" (the wrapper node that wraps all of the line's elements,
         *     including gutter elements). class should be the name of the class to apply.
         */
        fun addLineClass(line: Int, where: String, `class`: String): LineHandle

        /**
         Set a CSS class name for the given line. line can be a Number or a line handle. where determines to which element this class should be applied, can can be one of "text" (the text element, which lies in front of the selection), "background" (a background element that will be behind the selection), "gutter" (the line's gutter space), or "wrap" (the wrapper node that wraps all of the line's elements, including gutter elements). class should be the name of the class to apply.
         */
        fun addLineClass(line: LineHandle, where: String, `class`: String): LineHandle

        /**
         Remove a CSS class from a line. line can be a line handle or Number. where should be one of "text", "background", or "wrap" (see addLineClass). class can be left off to remove all classes for the specified node, or be a String to remove only a specific class.
         */
        fun removeLineClass(line: LineHandle, where: String, `class`: String): LineHandle

        /**
         Remove a CSS class from a line. line can be a line handle or Number. where should be one of "text", "background", or "wrap" (see addLineClass). class can be left off to remove all classes for the specified node, or be a String to remove only a specific class.
         */
        fun removeLineClass(line: Int, where: String, `class`: String): LineHandle

        /**
         Returns the line Number, text content, and marker status of the given line, which can be either a Number or a line handle. The returned object has the structure {line, handle, text, gutterMarkers, textClass, bgClass, wrapClass, widgets}, where gutterMarkers is an object mapping gutter IDs to marker elements, and widgets is an Array of line widgets attached to this line, and the various class properties refer to classes added with addLineClass.
         */
        fun lineInfo(line: LineHandle): LineInfo

        /**
         Returns the line Number, text content, and marker status of the given line, which can be either a Number or a line handle. The returned object has the structure {line, handle, text, gutterMarkers, textClass, bgClass, wrapClass, widgets}, where gutterMarkers is an object mapping gutter IDs to marker elements, and widgets is an Array of line widgets attached to this line, and the various class properties refer to classes added with addLineClass.
         */
        fun lineInfo(line: Int): LineInfo
        interface LineInfo {
            val line: Int
            val handle: dynamic?
            val text: String?
            val gutterMarker: dynamic?
            val textClass: String?
            val bgClass: String?
            val wrapClass: String?
            val widgets: dynamic
        }

        interface LineWidget {
            val line: LineHandle?

            /**
             Removes the widget.
             */
            fun clear()

            /**
             Call this if you made some change to the widget's DOM node that might affect its height. It'll force CodeMirror to update the height of the line that contains the widget.
             */
            fun changed()
        }

        /**
         Adds a line widget, an element shown below a line, spanning the whole of the editor's width, and moving the lines below it downwards. line should be either an Int or a line handle, and node should be a DOM node, which will be displayed below the given line. options, when given, should be an object that configures the behavior of the widget. The following options are supported (all default to false):
         */
        fun addLineWidget(
            line: Int,
            node: Element,
            options: LineWidgetOptions? = definedExternally
        ): LineWidget

        /**
         Adds a line widget, an element shown below a line, spanning the whole of the editor's width, and moving the lines below it downwards. line should be either an Int or a line handle, and node should be a DOM node, which will be displayed below the given line. options, when given, should be an object that configures the behavior of the widget. The following options are supported (all default to false):
         */
        fun addLineWidget(
            line: LineHandle,
            node: Element,
            options: LineWidgetOptions? = definedExternally
        ): LineWidget
    }

    interface TextMarker {
        fun clear()
        interface FindResult {
            val from: Location?
            val to: Location?
        }

        fun find(): FindResult?
        fun changed()
    }

//  ------------------------------------------------------------------------------------

    /**
     Each editor is associated with an instance of CodeMirror.Doc, its document. A document represents the editor content, plus a selection, an undo history, and a mode. A document can only be associated with a single editor at a time. You can create new documents by calling the CodeMirror.Doc(text: String, mode: Object, firstLineNumber: Number?, lineSeparator: String?) constructor. The last three arguments are optional and can be used to set a mode for the document, make it start at a line Number other than 0, and set a specific line separator respectively.
     Retrieve the currently active document from an editor.
     */
    fun getDoc(): Doc

    /**
     Attach a new document to the editor. Returns the old document, which is now no longer associated with an editor.
     */
    fun swapDoc(doc: Doc): Doc

    /**
     Tells you whether the editor currently has focus.
     */
    fun hasFocus(): Boolean

    /**
     Used to find the target position for horizontal cursor motion. start is a Location object, amount an Int (may be negative), and unit one of the String "char", "column", or "word". Will return a position that is produced by moving amount times the distance specified by unit. When visually is true, motion in right-to-left text will be visual rather than logical. When the motion was clipped by hitting the end or start of the document, the returned value will have a hitSide property set to true.
     */
    fun findPosH(start: Location, amount: Int, unit: String, visually: Boolean): Location

    /**
     Similar to findPosH, but used for vertical motion. unit may be "line" or "page". The other arguments and the returned value have the same interpretation as they have in findPosH.
     */
    fun findPosV(start: Location, amount: Int, unit: String): Location

    /**
     Returns the start and end of the 'word' (the stretch of letters, whitespace, or punctuation) at the given position.
     */
    fun findWordAt(pos: Location): WordPosition
    interface WordPosition {
        val anchor: Location
        val head: Location
    }

    /**
     Configuration methods
     */
    /**
     Change the configuration of the editor. option should the name of an option, and value should be a valid value for that option.
     */
    fun setOption(option: String, value: Any?)

    /**
     Retrieves the current value of the given option for this editor instance.
     */
    fun getOption(option: String): Any?

    /**
     Attach an additional key map to the editor. This is mostly useful for addons that need to register some key handlers without trampling on the extraKeys option. Maps added in this way have a higher precedence than the extraKeys and keyMap options, and between them, the maps added earlier have a lower precedence than those added later, unless the bottom argument was passed, in which case they end up below other key maps added with this method.
     */
    fun addKeyMap(map: dynamic, bottom: Boolean)

    /**
     Disable a keymap added with addKeyMap. Either pass in the key map object itself, or a String, which will be compared against the name property of the active key maps.
     */
    fun removeKeyMap(map: dynamic)

    /**
     Enable a highlighting overlay. This is a stateless mini-mode that can be used to add extra highlighting. For example, the search addon uses it to highlight the term that's currently being searched. mode can be a mode spec or a mode object (an object with a token method). The options parameter is optional. If given, it should be an object, optionally containing the following options:
     */
    fun addOverlay(mode: dynamic /* | String */, options: OverlayOptions? = definedExternally)

    /**
     Pass this the exact value passed for the mode parameter to addOverlay, or a String that corresponds to the name property of that value, to remove an overlay again.
     */
    fun removeOverlay(mode: dynamic /* | String */)

    /**
     Register an event handler for the given event type (a String) on the editor instance. There is also a CodeMirror.on(object, type, func) version that allows registering of events on any object.
     */
    fun on(type: String, func: dynamic)

    /**
     Remove an event handler on the editor instance. An equivalent CodeMirror.off(object, type, func) also exists.
     */
    fun off(type: String, func: dynamic)
    /**
     Sizing, scrolling and positioning methods
     */
    /**
     Puts node, which should be an absolutely positioned DOM node, into the editor, positioned right below the given Location position. When scrollIntoView is true, the editor will ensure that the entire node is visible (if possible). To remove the widget again, simply use DOM methods (move it somewhere else, or call removeChild on its parent).
     */
    fun addWidget(pos: Location, node: Element, scrollIntoView: Boolean)

    /**
     Programmatically set the size of the editor (overriding the applicable CSS rules). width and height can be either Numbers (interpreted as pixels) or CSS units ("100%", for example). You can pass null for either of them to indicate that that dimension should not be changed.
     */
    fun setSize(width: String, height: String)

    /**
     Programmatically set the size of the editor (overriding the applicable CSS rules). width and height can be either Numbers (interpreted as pixels) or CSS units ("100%", for example). You can pass null for either of them to indicate that that dimension should not be changed.
     */
    fun setSize(width: Number, height: Number)

    /**
     Scroll the editor to a given (pixel) position. Both arguments may be left as null or undefined to have no effect.
     */
    fun scrollTo(x: Number, y: Number)

    /**
     Get an {left, top, width, height, clientWidth, clientHeight} object that represents the current scroll position, the size of the scrollable area, and the size of the visible area (minus scrollbars).
     */
    interface Coordinates {

        val left: Int
        val top: Int
    }

    interface CoordinateRange : Coordinates {
        val right: Int?
        val bottom: Int
    }

    interface ScrollInfo : Coordinates {
        val width: Int
        val height: Int
        val clientWidth: Int
        val clientHeight: Int
    }

    fun getScrollInfo(): ScrollInfo

    /**
     Scrolls the given position into view. what may be null to scroll the cursor into view, a Location position to scroll a character into view, a {left, top, right, bottom} pixel range (in editor-local coordinates), or a range {from, to} containing either two character positions or two pixel squares. The margin parameter is optional. When given, it indicates the amount of vertical pixels around the given area that should be made visible as well.
     */
    fun scrollIntoView(what: Location?, margin: Number? = definedExternally)

    /**
     Scrolls the given position into view. what may be null to scroll the cursor into view, a Location position to scroll a character into view, a {left, top, right, bottom} pixel range (in editor-local coordinates), or a range {from, to} containing either two character positions or two pixel squares. The margin parameter is optional. When given, it indicates the amount of vertical pixels around the given area that should be made visible as well.
     */
    fun scrollIntoView(what: Coordinates, margin: Number? = definedExternally)

    /**
     Scrolls the given position into view. what may be null to scroll the cursor into view, a Location position to scroll a character into view, a {left, top, right, bottom} pixel range (in editor-local coordinates), or a range {from, to} containing either two character positions or two pixel squares. The margin parameter is optional. When given, it indicates the amount of vertical pixels around the given area that should be made visible as well.
     */
    fun scrollIntoView(what: CoordinateRange, margin: Number? = definedExternally)

    /**
     Returns an {left, top, bottom} object containing the coordinates of the cursor position. If mode is "local", they will be relative to the top-left corner of the editable document. If it is "page" or not given, they are relative to the top-left corner of the page. If mode is "window", the coordinates are relative to the top-left corner of the currently visible (scrolled) window. where can be a Boolean indicating whether you want the start (true) or the end (false) of the selection, or, if a Location object is given, it specifies the precise position at which you want to measure.
     */
    fun cursorCoords(where: Boolean, mode: String): CoordinateRange

    /**
     Returns an {left, top, bottom} object containing the coordinates of the cursor position. If mode is "local", they will be relative to the top-left corner of the editable document. If it is "page" or not given, they are relative to the top-left corner of the page. If mode is "window", the coordinates are relative to the top-left corner of the currently visible (scrolled) window. where can be a Boolean indicating whether you want the start (true) or the end (false) of the selection, or, if a Location object is given, it specifies the precise position at which you want to measure.
     */
    fun cursorCoords(where: Location, mode: String): CoordinateRange

    /**
     Returns the position and dimensions of an arbitrary character. pos should be a Location object. This differs from cursorCoords in that it'll give the size of the whole character, rather than just the position that the cursor would have when it would sit at that position.
     */
    fun charCoords(pos: Location, mode: String? = definedExternally): CoordinateRange

    /**
     Given an {left, top} object (e.g. coordinates of a mouse event) returns the Location position that corresponds to it. The optional mode parameter determines relative to what the coordinates are interpreted. It may be "window", "page" (the default), or "local".
     */
    fun coordsChar(`object`: Coordinates, mode: String? = definedExternally): Location

    /**
     Computes the line at the given pixel height. mode can be one of the same Strings that coordsChar accepts.
     */
    fun lineAtHeight(height: Number, mode: String? = definedExternally): Number

    /**
     Computes the height of the top of a line, in the coordinate system specified by mode (see coordsChar), which defaults to "page". When a line below the bottom of the document is specified, the returned value is the bottom of the last line in the document. By default, the position of the actual text is returned. If `includeWidgets` is true and the line has line widgets, the position above the first line widget is returned.
     */
    fun heightAtLine(
        line: Int,
        mode: String? = definedExternally,
        includeWidgets: Boolean? = definedExternally
    ): Number

    /**
     Computes the height of the top of a line, in the coordinate system specified by mode (see coordsChar), which defaults to "page". When a line below the bottom of the document is specified, the returned value is the bottom of the last line in the document. By default, the position of the actual text is returned. If `includeWidgets` is true and the line has line widgets, the position above the first line widget is returned.
     */
    fun heightAtLine(
        line: Doc.LineHandle,
        mode: String? = definedExternally,
        includeWidgets: Boolean? = definedExternally
    ): Number

    /**
     Returns the line height of the default font for the editor.
     */
    fun defaultTextHeight(): Number

    /**
     Returns the pixel width of an 'x' in the default font for the editor. (Note that for non-monospace fonts, this is mostly useless, and even for monospace fonts, non-ascii characters might have a different width).
     */
    fun defaultCharWidth(): Number

    /**
     Returns a {from, to} object indicating the start (inclusive) and end (exclusive) of the currently rendered part of the document. In big documents, when most content is scrolled out of view, CodeMirror will only render the visible part, and a margin around it. See also the viewportChange event.
     */
    interface Viewport {
        val from: Number
        val to: Number
    }

    fun getViewport(): Viewport

    /**

     If your code does something to change the size of the editor element (window resizes are already listened for), or unhides it, you should probably follow up by calling this method to ensure CodeMirror is still looking as intended. See also the autorefresh addon.
     */
    fun refresh()
    /**
     Mode, state, and token-related methods
     When writing language-aware functionality, it can often be useful to hook into the knowledge that the CodeMirror language mode has. See the section on modes for a more detailed description of how these work.
     */
    /**
     Gets the inner mode at a given position. This will return the same as getMode for simple modes, but will return an inner mode for nesting modes (such as htmlmixed).
     */
    fun getModeAt(pos: Location): dynamic

    /**
     Retrieves information about the token the current mode found before the given position (a Location object). The returned object has the following properties:
     */
    fun getTokenAt(pos: Location, precise: Boolean? = definedExternally): Token

    interface Token {
        /**
         The character (on the given line) at which the token starts.
         */
        val start: Int

        /**
         The character at which the token ends.
         */
        val end: Int

        /**
         The token's String.
         */
        val String: Int

        /**
         The token type the mode assigned to the token, such as "keyword" or "comment" (may also be null).
         */
        val type: String

        /**
         If precise is true, the token will be guaranteed to be accurate based on recent edits. If false or not specified, the token will use cached state information, which will be faster but might not be accurate if edits were recently made and highlighting has not yet completed.
         The mode's state at the end of this token.
         */
        val state: dynamic
    }

    interface helper

    /**
     This is similar to getTokenAt, but collects all tokens for a given line into an Array. It is much cheaper than repeatedly calling getTokenAt, which re-parses the part of the line before the token for every call.
     */
    fun getLineTokens(line: Int, precise: Boolean? = definedExternally): Array<Token>

    /**
     This is a (much) cheaper version of getTokenAt useful for when you just need the type of the token at a given position, and no other information. Will return null for unstyled tokens, and a String, potentially containing multiple space-separated style names, otherwise.
     */
    fun getTokenTypeAt(pos: Location): String

    /**
     Fetch the set of applicable helper values for the given position. Helpers provide a way to look up functionality appropriate for a mode. The type argument provides the helper namespace (see registerHelper), in which the values will be looked up. When the mode itself has a property that corresponds to the type, that directly determines the keys that are used to look up the helper values (it may be either a single String, or an Array of Strings). Failing that, the mode's helperType property and finally the mode's name are used.
     */
    fun getHelpers(pos: Location, type: String): Array<helper>
    /**
     When any 'global' helpers are defined for the given namespace, their predicates are called on the current mode and editor, and all those that declare they are applicable will also be added to the Array that is returned.
     For example, the JavaScript mode has a property fold containing "brace". When the brace-fold addon is loaded, that defines a helper named brace in the fold namespace. This is then used by the foldcode addon to figure out that it can use that folding function to fold JavaScript code.
     */
    /**
     Returns the first applicable helper value. See getHelpers.
     */
    fun getHelper(pos: Location, type: String): helper

    /**
     Returns the mode's parser state, if any, at the end of the given line Number. If no line Number is given, the state at the end of the document is returned. This can be useful for storing parsing errors in the state, or getting other kinds of contextual information for a line. precise is defined as in getTokenAt().
     */
    fun getStateAfter(
        line: Int? = definedExternally,
        precise: Boolean? = definedExternally
    ): dynamic

    /**
     Miscellaneous methods
     */
    /**
     CodeMirror internally buffers changes and only updates its DOM structure after it has finished performing some operation. If you need to perform a lot of operations on a CodeMirror instance, you can call this method with a function argument. It will call the function, buffering up all changes, and only doing the expensive update after the function returns. This can be a lot faster. The return value from this method will be the return value of your function.
     */
    fun operation(func: () -> Any): Any

    /**
     In normal circumstances, use the above operation method. But if you want to buffer operations happening asynchronously, or that can't all be wrapped in a callback function, you can call startOperation to tell CodeMirror to start buffering changes, and endOperation to actually render all the updates. Be careful: if you use this API and forget to call endOperation, the editor will just never update.
     */
    fun startOperation()

    /**
     */
    fun endOperation()

    /**
     Adjust the indentation of the given line. The second argument (which defaults to "smart") may be one of:
     "prev" Base indentation on the indentation of the previous line.
     "smart" Use the mode's smart indentation if available, behave like "prev" otherwise.
     "add" Increase the indentation of the line by one indent unit.
     "subtract" Reduce the indentation of the line.
     <Int> Add (positive Number) or reduce (negative Number) the indentation by the given amount of spaces.
     */
    fun indentLine(line: Int, dir: Int? = definedExternally)

    /**
     Adjust the indentation of the given line. The second argument (which defaults to "smart") may be one of:
     "prev" Base indentation on the indentation of the previous line.
     "smart" Use the mode's smart indentation if available, behave like "prev" otherwise.
     "add" Increase the indentation of the line by one indent unit.
     "subtract" Reduce the indentation of the line.
     <Int> Add (positive Number) or reduce (negative Number) the indentation by the given amount of spaces.
     */
    fun indentLine(line: Int, dir: String)

    /**
     Switches between overwrite and normal insert mode (when not given an argument), or sets the overwrite mode to a specific state (when given an argument).
     */
    fun toggleOverwrite(value: Boolean? = definedExternally)

    /**
     Tells you whether the editor's content can be edited by the user.
     */
    fun isReadOnly(): Boolean

    /**
     Runs the command with the given name on the editor.
     */
    fun execCommand(name: String)

    /**
     Give the editor focus.
     */
    fun focus()

    /**
     Allow the given String to be translated with the phrases option.
     */
    fun phrase(text: String): String

    /**
     Returns the input field for the editor. Will be a textarea or an editable div, depending on the value of the inputStyle option.
     */
    fun getInputField(): Element

    /**
     Returns the DOM node that represents the editor, and controls its size. Remove this from your tree to delete an editor instance.
     */
    fun getWrapperElement(): Element

    /**
     Returns the DOM node that is responsible for the scrolling of the editor.
     */
    fun getScrollerElement(): Element

    /**
     Fetches the DOM node that contains the editor gutters.
     */
    fun getGutterElement(): Element

    /**
     Copy the content of the editor into the textarea.
     */
    fun save()

    /**
     Remove the editor, and restore the original textarea (with the editor's current content). If you dynamically create and destroy editors made with `fromTextArea`, without destroying the form they are part of, you should make sure to call `toTextArea` to remove the editor, or its `"submit"` handler on the form will cause a memory leak.
     */
    fun toTextArea()

    /**
     Returns the textarea that the instance was based on.
     */
    fun getTextArea(): TEXTAREA
    /**
     Static properties
     */
    /**

     */
    companion object {
        /**
         The CodeMirror object itself provides several useful properties.
         It contains a String that indicates the version of the library. This is a triple of Ints "major.minor.patch", where patch is zero for releases, and something else (usually one) for dev snapshots.
         */
        val version: String

        /**
         This method provides another way to initialize an editor. It takes a textarea DOM node as first argument and an optional configuration object as second. It will replace the textarea with a CodeMirror instance, and wire up the form of that textarea (if any) to make sure the editor contents are put into the textarea when the form is submitted. The text in the textarea will provide the content for the editor. A CodeMirror instance created this way has three additional methods:
         */
        fun fromTextArea(textArea: TEXTAREA, config: dynamic? = definedExternally): CodeMirror

        /**
         An object containing default values for all options. You can assign to its properties to modify defaults (though this won't affect editors that have already been created).
         */
        val defaults: dynamic
        val commands: dynamic

        /**
         If you want to define extra methods in terms of the CodeMirror API, it is possible to use defineExtension. This will cause the given value (usually a method) to be added to all CodeMirror instances created from then on.
         */
        fun defineExtension(name: String, value: dynamic)

        /**
         Like defineExtension, but the method will be added to the interface for Doc objects instead.
         */
        fun defineDocExtension(name: String, value: dynamic)

        /**
         Similarly, defineOption can be used to define new options for CodeMirror. The updateFunc will be called with the editor instance and the new value when an editor is initialized, and whenever the option is modified through setOption.
         */
        fun defineOption(name: String, default: dynamic, updateFunc: () -> Unit)

        /**
         If your extension just needs to run some code whenever a CodeMirror instance is initialized, use CodeMirror.defineInitHook. Give it a function as its only argument, and from then on, that function will be called (with the instance as argument) whenever a new CodeMirror instance is initialized.
         */
        fun defineInitHook(func: () -> Unit)

        /**
         Registers a helper value with the given name in the given namespace (type). This is used to define functionality that may be looked up by mode. Will create (if it doesn't already exist) a property on the CodeMirror object for the given type, pointing to an object that maps names to values. I.e. after doing CodeMirror.registerHelper("hint", "foo", myFoo), the value CodeMirror.hint.foo will point to myFoo.
         */
        fun registerHelper(type: String, name: String, value: helper)

        /**
         Acts like registerHelper, but also registers this helper as 'global', meaning that it will be included by getHelpers whenever the given predicate returns true when called with the local mode and editor.
         */
        fun registerGlobalHelper(
            type: String,
            name: String,
            predicate: (dynamic, CodeMirror) -> Unit,
            value: helper
        )

        /**
         A constructor for the objects that are used to represent positions in editor documents. sticky defaults to null, but can be set to "before" or "after" to make the position explicitly associate with the character before or after it.
         */
        fun Pos(line: Int, ch: Int? = definedExternally, sticky: String? = definedExternally)
        interface Range {
            val from: Int
            val to: Int
            val text: String
        }

        /**
         Utility function that computes an end position from a change (an object with from, to, and text properties, as passed to various event handlers). The returned position will be the end of the changed range, after the change is applied.
         */
        fun changeEnd(change: dynamic): Location

        /**
         Find the column position at a given String index using a given tabsize.
         */
        fun countColumn(line: String, index: Number, tabSize: Number): Number

        /**
         Register an event handler for the given event type (a String) on the editor instance. There is also a CodeMirror.on(object, type, func) version that allows registering of events on any object.
         */
        fun on(`object`: dynamic, type: String, func: dynamic)

        /**
         Remove an event handler on the editor instance. An equivalent CodeMirror.off(object, type, func) also exists.
         */
        fun off(`object`: dynamic, type: String, func: dynamic)
    }
}

external interface SelectionOptions {
    /**
     Determines whether the selection head should be scrolled into view. Defaults to true.
     */
    val scroll: Boolean

    /**
     Determines whether the selection history event may be merged with the previous one. When an origin starts with the character +, and the last recorded selection had the same origin and was similar (close in time, both collapsed or both non-collapsed), the new one will replace the old one. When it starts with *, it will always replace the previous event (if that had the same origin). Built-in motion uses the "+move" origin. User input uses the "+input" origin.
     */
    val origin: String

    /**
     Determine the direction into which the selection endpoints should be adjusted when they fall inside an atomic range. Can be either -1 (backward) or 1 (forward). When not given, the bias will be based on the relative position of the old selection—the editor will try to move further away from that, to prevent getting stuck.
     */
    val bias: Number
}

external interface LinkOptions {
    /**
     When turned on, the linked copy will share an undo history with the original. Thus, something done in one of the two can be undone in the other, and vice versa.
     */
    val sharedHist: Boolean

    /**
     Can be given to make the new document a subview of the original. Subviews only show a given range of lines. Note that line coordinates inside the subview will be consistent with those of the parent, so that for example a subview starting at line 10 will refer to its first line as line 10, not 0.
     */
    val to: Int
    val from: Int

    /**
     By default, the new document inherits the mode of the parent. This option can be set to a mode spec to give it a different mode.
     */
    val mode: Any /* | String */
}

external interface MarkTextOptions {
    /**
     Assigns a CSS class to the marked stretch of text.
     */
    val className: String?

    /**
     Determines whether text inserted on the left of the marker will end up inside or outside of it.
     */
    val inclusiveLeft: Boolean?

    /**
     Like inclusiveLeft, but for the right side.
     */
    val inclusiveRight: Boolean?

    /**
     For atomic ranges, determines whether the cursor is allowed to be placed directly to the left of the range. Has no effect on non-atomic ranges.
     */
    val selectLeft: Boolean?

    /**
     Like selectLeft, but for the right side.
     */
    val selectRight: Boolean?

    /**
     Atomic ranges act as a single unit when cursor movement is concerned—i.e. it is impossible to place the cursor inside of them. You can control whether the cursor is allowed to be placed directly before or after them using selectLeft or selectRight. If selectLeft {or right} is not provided, then inclusiveLeft {or right} will control this behavior.
     */
    val atomic: Boolean?

    /**
     Collapsed ranges do not show up in the display. Setting a range to be collapsed will automatically make it atomic.
     */
    val collapsed: Boolean?

    /**
     When enabled, will cause the mark to clear itself whenever the cursor enters its range. This is mostly useful for text-replacement widgets that need to 'snap open' when the user tries to edit them. The "clear" event fired on the range handle can be used to be notified when this happens.
     */
    val clearOnEnter: Boolean?

    /**
     Determines whether the mark is automatically cleared when it becomes empty. Default is true.
     */
    val clearWhenEmpty: Boolean?

    /**
     Use a given node to display this range. Implies both collapsed and atomic. The given DOM node must be an inline element {as opposed to a block element}.
     */
    val replacedWith: Element?

    /**
     When replacedWith is given, this determines whether the editor will capture mouse and drag events occurring in this widget. Default is false—the events will be left alone for the default browser handler, or specific handlers on the widget, to capture.
     */
    val handleMouseEvents: Boolean?

    /**
     A read-only span can, as long as it is not cleared, not be modified except by calling setValue to reset the whole document. Note: adding a read-only span currently clears the undo history of the editor, because existing undo events being partially nullified by read-only spans would corrupt the history {in the current implementation}.
     */
    val readOnly: Boolean?

    /**
     When set to true {default is false}, adding this marker will create an event in the undo history that can be individually undone {clearing the marker}.
     */
    val addToHistory: Boolean?

    /**
     Can be used to specify an extra CSS class to be applied to the leftmost span that is part of the marker.
     */
    val startStyle: String?

    /**
     Equivalent to startStyle, but for the rightmost span.
     */
    val endStyle: String?

    /**
     A String of CSS to be applied to the covered text. For example "color: #fe3".
     */
    val css: String?

    /**
     When given, add the attributes in the given object to the elements created for the marked text. Adding class or style attributes this way is not supported.
     */
    val attributes: Any?

    /**
     When the target document is linked to other documents, you can set shared to true to make the marker appear in all documents. By default, a marker appears only in its target document.
     */
    val shared: Boolean?
}

external interface BookmarkOptions {
    /**
     Can be used to display a DOM node at the current location of the bookmark {analogous to the replacedWith option to markText}.
     */
    val widget: Element

    /**
     By default, text typed when the cursor is on top of the bookmark will end up to the right of the bookmark. Set this option to true to make it go to the left instead.
     */
    val insertLeft: Boolean

    /**
     See the corresponding option to markText.
     */
    val shared: Boolean

    /**
     As with markText, this determines whether mouse events on the widget inserted for this bookmark are handled by CodeMirror. The default is false.
     */
    val handleMouseEvents: Boolean
}

external interface LineWidgetOptions {
    /**
     Whether the widget should cover the gutter.
     */
    val coverGutter: Boolean

    /**
     Whether the widget should stay fixed in the face of horizontal scrolling.
     */
    val noHScroll: Boolean

    /**
     Causes the widget to be placed above instead of below the text of the line.
     */
    val above: Boolean

    /**
     Determines whether the editor will capture mouse and drag events occurring in this widget. Default is false—the events will be left alone for the default browser handler, or specific handlers on the widget, to capture.
     */
    val handleMouseEvents: Boolean

    /**
     By default, the widget is added below other widgets for the line. This option can be used to place it at a different position {zero for the top, N to put it after the Nth other widget}. Note that this only has effect once, when the widget is created.
     */
    val insertAt: Int

    /**
     Note that the widget node will become a descendant of nodes with CodeMirror-specific CSS classes, and those classes might in some cases affect it. This method returns an object that represents the widget placement. It'll have a line property pointing at the line handle that it is associated with, and the following methods:
     Add an extra CSS class name to the wrapper element created for the widget.
     */
    val className: String
}

external interface OverlayOptions {
    /**
     Defaults to off, but can be given to allow the overlay styling, when not null, to the styling of the base mode entirely, instead of the two being applied together.  priority: Number Determines the ordering in which the overlays are applied. Those with high priority are applied after those with lower priority, and able to the opaqueness of the ones that come before. Defaults to 0.
     */
    val opaque: Boolean
}

external interface Location {
    val line: Int
    val ch: Int
    val hitSide: Boolean?
}

data class SelectionOptionsImpl(
    /**
     Determines whether the selection head should be scrolled into view. Defaults to true.
     */
    override val scroll: Boolean,

    /**
     Determines whether the selection history event may be merged with the previous one. When an origin starts with the character +, and the last recorded selection had the same origin and was similar (close in time, both collapsed or both non-collapsed), the new one will replace the old one. When it starts with *, it will always replace the previous event (if that had the same origin). Built-in motion uses the "+move" origin. User input uses the "+input" origin.
     */
    override val origin: String,

    /**
     Determine the direction into which the selection endpoints should be adjusted when they fall inside an atomic range. Can be either -1 (backward) or 1 (forward). When not given, the bias will be based on the relative position of the old selection—the editor will try to move further away from that, to prevent getting stuck.
     */
    override val bias: Number
) : SelectionOptions

data class LinkOptionsImpl(
    /**
     When turned on, the linked copy will share an undo history with the original. Thus, something done in one of the two can be undone in the other, and vice versa.
     */
    override val sharedHist: Boolean,
    /**
     Can be given to make the new document a subview of the original. Subviews only show a given range of lines. Note that line coordinates inside the subview will be consistent with those of the parent, so that for example a subview starting at line 10 will refer to its first line as line 10, not 0.
     */
    override val to: Int,
    override val from: Int,
    /**
     By default, the new document inherits the mode of the parent. This option can be set to a mode spec to give it a different mode.
     */
    override val mode: Any /* | String */
) : LinkOptions

data class MarkTextOptionsImpl(
    /**
     Assigns a CSS class to the marked stretch of text.
     */
    override val className: String? = undefined,
    /**
     Determines whether text inserted on the left of the marker will end up inside or outside of it.
     */
    override val inclusiveLeft: Boolean? = undefined,
    /**
     Like inclusiveLeft, but for the right side.
     */
    override val inclusiveRight: Boolean? = undefined,
    /**
     For atomic ranges, determines whether the cursor is allowed to be placed directly to the left of the range. Has no effect on non-atomic ranges.
     */
    override val selectLeft: Boolean? = undefined,
    /**
     Like selectLeft, but for the right side.
     */
    override val selectRight: Boolean? = undefined,
    /**
     Atomic ranges act as a single unit when cursor movement is concerned—i.e. it is impossible to place the cursor inside of them. You can control whether the cursor is allowed to be placed directly before or after them using selectLeft or selectRight. If selectLeft (or right) is not provided, then inclusiveLeft (or right) will control this behavior.
     */
    override val atomic: Boolean? = undefined,
    /**
     Collapsed ranges do not show up in the display. Setting a range to be collapsed will automatically make it atomic.
     */
    override val collapsed: Boolean? = undefined,
    /**
     When enabled, will cause the mark to clear itself whenever the cursor enters its range. This is mostly useful for text-replacement widgets that need to 'snap open' when the user tries to edit them. The "clear" event fired on the range handle can be used to be notified when this happens.
     */
    override val clearOnEnter: Boolean? = undefined,
    /**
     Determines whether the mark is automatically cleared when it becomes empty. Default is true.
     */
    override val clearWhenEmpty: Boolean? = undefined,
    /**
     Use a given node to display this range. Implies both collapsed and atomic. The given DOM node must be an inline element (as opposed to a block element).
     */
    override val replacedWith: Element? = undefined,
    /**
     When replacedWith is given, this determines whether the editor will capture mouse and drag events occurring in this widget. Default is false—the events will be left alone for the default browser handler, or specific handlers on the widget, to capture.
     */
    override val handleMouseEvents: Boolean? = undefined,
    /**
     A read-only span can, as long as it is not cleared, not be modified except by calling setValue to reset the whole document. Note: adding a read-only span currently clears the undo history of the editor, because existing undo events being partially nullified by read-only spans would corrupt the history (in the current implementation).
     */
    override val readOnly: Boolean? = undefined,
    /**
     When set to true (default is false), adding this marker will create an event in the undo history that can be individually undone (clearing the marker).
     */
    override val addToHistory: Boolean? = undefined,
    /**
     Can be used to specify an extra CSS class to be applied to the leftmost span that is part of the marker.
     */
    override val startStyle: String? = undefined,
    /**
     Equivalent to startStyle, but for the rightmost span.
     */
    override val endStyle: String? = undefined,
    /**
     A String of CSS to be applied to the covered text. For example "color: #fe3".
     */
    override val css: String? = undefined,
    /**
     When given, add the attributes in the given object to the elements created for the marked text. Adding class or style attributes this way is not supported.
     */
    override val attributes: Any? = undefined,
    /**
     When the target document is linked to other documents, you can set shared to true to make the marker appear in all documents. By default, a marker appears only in its target document.
     */
    override val shared: Boolean? = undefined
) : MarkTextOptions

data class BookmarkOptionsImpl(
    /**
     Can be used to display a DOM node at the current location of the bookmark (analogous to the replacedWith option to markText).
     */
    override val widget: Element,
    /**
     By default, text typed when the cursor is on top of the bookmark will end up to the right of the bookmark. Set this option to true to make it go to the left instead.
     */
    override val insertLeft: Boolean,
    /**
     See the corresponding option to markText.
     */
    override val shared: Boolean,

    /**
     As with markText, this determines whether mouse events on the widget inserted for this bookmark are handled by CodeMirror. The default is false.
     */
    override val handleMouseEvents: Boolean
) : BookmarkOptions

data class LineWidgetOptionsImpl(
    /**
     Whether the widget should cover the gutter.
     */
    override val coverGutter: Boolean,
    /**
     Whether the widget should stay fixed in the face of horizontal scrolling.
     */
    override val noHScroll: Boolean,
    /**
     Causes the widget to be placed above instead of below the text of the line.
     */
    override val above: Boolean,
    /**
     Determines whether the editor will capture mouse and drag events occurring in this widget. Default is false—the events will be left alone for the default browser handler, or specific handlers on the widget, to capture.
     */
    override val handleMouseEvents: Boolean,
    /**
     By default, the widget is added below other widgets for the line. This option can be used to place it at a different position (zero for the top, N to put it after the Nth other widget). Note that this only has effect once, when the widget is created.
     */
    override val insertAt: Int,
    /**
     Note that the widget node will become a descendant of nodes with CodeMirror-specific CSS classes, and those classes might in some cases affect it. This method returns an object that represents the widget placement. It'll have a line property pointing at the line handle that it is associated with, and the following methods:
     Add an extra CSS class name to the wrapper element created for the widget.
     */
    override val className: String
) : LineWidgetOptions

data class OverlayOptionsImpl(
    /**
     Defaults to off, but can be given to allow the overlay styling, when not null, to override the styling of the base mode entirely, instead of the two being applied together.  priority: Number Determines the ordering in which the overlays are applied. Those with high priority are applied after those with lower priority, and able to override the opaqueness of the ones that come before. Defaults to 0.
     */
    override val opaque: Boolean
) : OverlayOptions

data class LocationImpl(
    override val line: Int,
    override val ch: Int,
    override val hitSide: Boolean? = null
) : Location

sealed class CodeMirrorEvent<F>(val name: String) {
    fun addListener(target: CodeMirror, function: F) {
        target.on(name, function)
    }

    fun removeListener(target: CodeMirror, function: F) {
        target.off(name, function)
    }
}

sealed class OtherCodeMirrorEvent<T, F>(val name: String) {
    fun addListener(target: T, function: F) {
        CodeMirror.on(target, name, function)
    }

    fun removeListener(target: T, function: F) {
        CodeMirror.off(target, name, function)
    }
}

// CodeMirror events.
external interface ChangeEvent {
    val from: Location?
    val to: Location?
    val text: String?
    val removed: String?
    val origin: Any?
}

/** Fires every time the content of the editor is changed.
 * The changeObj is a {from, to, text, removed, origin}
 * object containing information about the changes that occurred as second argument.
 * from and to are the positions (in the pre-change coordinate system) where the change started and ended (for example, it might be {ch:0, line:18} if the position is at the beginning of line #19).
 * text is an array of strings representing the text that replaced the changed range (split by line).
 * removed is the text that used to be between from and to, which is overwritten by this change.
 * This event is fired before the end of an operation, before the DOM updates happen. */
object OnChange : CodeMirrorEvent<(CodeMirror, ChangeEvent) -> Unit>("change")

/** Like the "change" event, but batched per operation, passing an array containing all the changes that happened in the operation. This event is fired after the operation finished, and display changes it makes will trigger a new operation. */
object OnChanges : CodeMirrorEvent<(CodeMirror, Array<ChangeEvent>) -> Unit>("changes")

/** This event is fired before a change is applied, and its handler may choose to modify or cancel the change. The changeObj object has from, to, and text properties, as with the "change" event. It also has a cancel() method, which can be called to cancel the change, and, if the change isn't coming from an undo or redo event, an update(from, to, text) method, which may be used to modify the change. Undo or redo changes can't be modified, because they hold some metainformation for restoring old marked ranges that is only valid for that specific change. All three arguments to update are optional, and can be left off to leave the existing value for that field intact. Note: you may not do anything from a "beforeChange" handler that would cause changes to the document or its visualization. Doing so will, since this handler is called directly from the bowels of the CodeMirror implementation, probably cause the editor to become corrupted. */

external interface CancellableChangeEvent : ChangeEvent {
    fun cancel()
    fun update(
        from: Location? = definedExternally,
        to: Location? = definedExternally,
        text: String? = definedExternally
    )
}

object BeforeChange : CodeMirrorEvent<(CodeMirror, CancellableChangeEvent) -> Unit>("beforeChange")

/** Will be fired when the cursor or selection moves, or any change is made to the editor content. */
object OnCursorActivity : CodeMirrorEvent<(CodeMirror) -> Unit>("cursorActivity")

/** Fired after a key is handled through a key map. name is the name of the handled key (for example "Ctrl-X" or "'q'"), and event is the DOM keydown or keypress event. */
object OnKeyHandled : CodeMirrorEvent<(CodeMirror, String, Event) -> Unit>("keyHandled")

/** Fired whenever new input is read from the hidden textarea (typed or pasted by the user). */
object OnInputRead : CodeMirrorEvent<(CodeMirror, Any) -> Unit>("inputRead")

/** Fired if text input matched the mode's electric patterns, and this caused the line's indentation to change. */
object OnElectricInput : CodeMirrorEvent<(CodeMirror, Int) -> Unit>("electricInput")

/** This event is fired before the selection is moved. Its handler may inspect the set of selection ranges, present as an array of {anchor, head} objects in the ranges property of the obj argument, and optionally change them by calling the update method on this object, passing an array of ranges in the same format. The object also contains an origin property holding the origin string passed to the selection-changing method, if any. Handlers for this event have the same restriction as "beforeChange" handlers — they should not do anything to directly update the state of the editor. */
external interface SelectionChangeEvent {
    val ranges: Array<CodeMirror.Doc.Selection>?
    val origin: String?
    fun update(ranges: Array<CodeMirror.Doc.Selection>)
}

object BeforeSelectionChange :
    CodeMirrorEvent<(CodeMirror, SelectionChangeEvent) -> Unit>("beforeSelectionChange")

/** Fires whenever the view port of the editor changes (due to scrolling, editing, or any other factor). The from and to arguments give the new start and end of the viewport. */
object OnViewportChange : CodeMirrorEvent<(CodeMirror, Number, Number) -> Unit>("viewportChange")

/** This is signalled when the editor's document is replaced using the swapDoc method. */
object OnSwapDoc : CodeMirrorEvent<(CodeMirror, CodeMirror.Doc) -> Unit>("swapDoc")

/** Fires when the editor gutter (the line-number area) is clicked. Will pass the editor instance as first argument, the (zero-based) number of the line that was clicked as second argument, the CSS class of the gutter that was clicked as third argument, and the raw mousedown event object as fourth argument. */
object OnGutterClick : CodeMirrorEvent<(CodeMirror, Int, String, Event) -> Unit>("gutterClick")

/** Fires when the editor gutter (the line-number area) receives a contextmenu event. Will pass the editor instance as first argument, the (zero-based) number of the line that was clicked as second argument, the CSS class of the gutter that was clicked as third argument, and the raw contextmenu mouse event object as fourth argument. You can preventDefault the event, to signal that CodeMirror should do no further handling. */
object OnGutterContextMenu :
    CodeMirrorEvent<(CodeMirror, Int, String, Event) -> Unit>("gutterContextMenu")

/** Fires whenever the editor is focused. */
object OnFocus : CodeMirrorEvent<(CodeMirror, Event) -> Unit>("focus")

/** Fires whenever the editor is unfocused. */
object OnBlur : CodeMirrorEvent<(CodeMirror, Event) -> Unit>("blur")

/** Fires when the editor is scrolled. */
object OnScroll : CodeMirrorEvent<(CodeMirror) -> Unit>("scroll")

/** Fires when the editor is refreshed or resized. Mostly useful to invalidate cached values that depend on the editor or character size. */
object OnRefresh : CodeMirrorEvent<(CodeMirror) -> Unit>("refresh")

/** Dispatched every time an option is changed with setOption. */
object OnOptionChange : CodeMirrorEvent<(CodeMirror, String) -> Unit>("optionChange")

/** Fires when the editor tries to scroll its cursor into view. Can be hooked into to take care of additional scrollable containers around the editor. When the event object has its preventDefault method called, CodeMirror will not itself try to scroll the window. */
object OnScrollCursorIntoView : CodeMirrorEvent<(CodeMirror, Event) -> Unit>("scrollCursorIntoView")

/** Will be fired whenever CodeMirror updates its DOM display. */
object OnUpdate : CodeMirrorEvent<(CodeMirror) -> Unit>("update")

/** Fired whenever a line is (re-)rendered to the DOM. Fired right after the DOM element is built, before it is added to the document. The handler may mess with the style of the resulting element, or add event handlers, but should not try to change the state of the editor. */
object OnRenderLine :
    CodeMirrorEvent<(CodeMirror, CodeMirror.Doc.LineHandle, Element) -> Unit>("renderLine")

/** Fired when CodeMirror is handling a DOM event of this type. You can preventDefault the event, or give it a truthy codemirrorIgnore property, to signal that CodeMirror should do no further handling. */
object OnMouseDown : CodeMirrorEvent<(CodeMirror, Event) -> Unit>("mousedown")
object OnDblClick : CodeMirrorEvent<(CodeMirror, Event) -> Unit>("dblclick")
object OnTouchStart : CodeMirrorEvent<(CodeMirror, Event) -> Unit>("touchstart")
object OnContextMenu : CodeMirrorEvent<(CodeMirror, Event) -> Unit>("contextmenu")
object OnKeyDown : CodeMirrorEvent<(CodeMirror, Event) -> Unit>("keydown")
object OnKeyPress : CodeMirrorEvent<(CodeMirror, Event) -> Unit>("keypress")
object OnKeyUp : CodeMirrorEvent<(CodeMirror, Event) -> Unit>("keyup")
object OnCut : CodeMirrorEvent<(CodeMirror, Event) -> Unit>("cut")
object OnCopy : CodeMirrorEvent<(CodeMirror, Event) -> Unit>("copy")
object OnPaste : CodeMirrorEvent<(CodeMirror, Event) -> Unit>("paste")
object OnDragStart : CodeMirrorEvent<(CodeMirror, Event) -> Unit>("dragstart")
object OnDragEnter : CodeMirrorEvent<(CodeMirror, Event) -> Unit>("dragenter")
object OnDragOver : CodeMirrorEvent<(CodeMirror, Event) -> Unit>("dragover")
object OnDragLeave : CodeMirrorEvent<(CodeMirror, Event) -> Unit>("dragleave")
object OnDrop : CodeMirrorEvent<(CodeMirror, Event) -> Unit>("drop")

/** Document objects (instances of CodeMirror.Doc) emit the following events: */

/** Fired whenever a change occurs to the document. changeObj has a similar type as the object passed to the editor's "change" event. */
object OnDocChange :
    OtherCodeMirrorEvent<CodeMirror.Doc, (CodeMirror.Doc, ChangeEvent) -> Unit>("change")

/** See the description of the same event on editor instances. */
object BeforeDocChange :
    OtherCodeMirrorEvent<CodeMirror.Doc, (CodeMirror.Doc, CancellableChangeEvent) -> Unit>("beforeChange")

/** Fired whenever the cursor or selection in this document changes. */
object OnDocCursorActivity :
    OtherCodeMirrorEvent<CodeMirror.Doc, (CodeMirror.Doc) -> Unit>("cursorActivity")

/** Equivalent to the event by the same name as fired on editor instances. */
object BeforeDocSelectionChange :
    OtherCodeMirrorEvent<CodeMirror.Doc, (CodeMirror.Doc, SelectionChangeEvent) -> Unit>("beforeSelectionChange")

/** Line handles (as returned by, for example, getLineHandle) support these events: */

/** Will be fired when the line object is deleted. A line object is associated with the start of the line. Mostly useful when you need to find out when your gutter markers on a given line are removed. */
object OnLineHandleDelete : OtherCodeMirrorEvent<CodeMirror.Doc.LineHandle, () -> Unit>("delete")

/** Fires when the line's text content is changed in any way (but the line is not deleted outright). The change object is similar to the one passed to change event on the editor object. */
object OnLineHandleChange :
    OtherCodeMirrorEvent<CodeMirror.Doc.LineHandle, (CodeMirror.Doc.LineHandle, ChangeEvent) -> Unit>(
        "change"
    )

/** Marked range handles (CodeMirror.TextMarker), as returned by markText and setBookmark, emit the following events: */

/** Fired when the cursor enters the marked range. From this event handler, the editor state may be inspected but not modified, with the exception that the range on which the event fires may be cleared. */
object BeforeCursorEnter :
    OtherCodeMirrorEvent<CodeMirror.TextMarker, () -> Unit>("beforeCursorEnter")

/** Fired when the range is cleared, either through cursor movement in combination with clearOnEnter or through a call to its clear() method. Will only be fired once per handle. Note that deleting the range through text editing does not fire this event, because an undo action might bring the range back into existence. from and to give the part of the document that the range spanned when it was cleared. */
object OnClear : OtherCodeMirrorEvent<CodeMirror.TextMarker, (Location, Location) -> Unit>("clear")

/** Fired when the last part of the marker is removed from the document by editing operations. */
object OnHide : OtherCodeMirrorEvent<CodeMirror.TextMarker, () -> Unit>("hide")

/** Fired when, after the marker was removed by editing, a undo operation brought the marker back. */
object OnUnHide : OtherCodeMirrorEvent<CodeMirror.TextMarker, () -> Unit>("unhide")

/** Line widgets (CodeMirror.LineWidget), returned by addLineWidget, fire these events: */

/** Fired whenever the editor re-adds the widget to the DOM. This will happen once right after the widget is added (if it is scrolled into view), and then again whenever it is scrolled out of view and back in again, or when changes to the editor options or the line the widget is on require the widget to be redrawn. */
object OnRedraw : OtherCodeMirrorEvent<CodeMirror.Doc.LineWidget, () -> Unit>("redraw")

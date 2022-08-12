@file:Suppress("UNCHECKED_CAST")

package com.monkopedia.konstructor.frontend.editor

import com.monkopedia.konstructor.frontend.utils.buildExt
import dukat.codemirror.state.Range
import dukat.codemirror.state.RangeFilter
import dukat.codemirror.state.RangeSet
import dukat.codemirror.state.StateEffect
import dukat.codemirror.state.StateEffectType
import dukat.codemirror.state.StateField
import dukat.codemirror.state.StateFieldSpec
import dukat.codemirror.view.Decoration
import dukat.codemirror.view.DecorationSet
import dukat.codemirror.view.EditorView

// Effects can be attached to transactions to communicate with the extension
val addMarks: StateEffectType<Array<Range<Decoration>>> = StateEffect.define()
val filterMarks: StateEffectType<RangeFilter<Decoration>> = StateEffect.define()

// This value must be added to the set of extensions to enable this
val markField = StateField.define(
    buildExt<StateFieldSpec<DecorationSet>> {
        // Start with an empty set of decorations
        this.create = { Decoration.none }
        // This is called whenever the editor updates—it computes the new set
        this.update = { value, tr ->
            /* Move the decorations to account for document changes */
            var value = value.map(tr.changes)
            // If this transaction adds or removes decorations, apply those changes
            for (effect in tr.effects) {
                if (effect.`is`(addMarks)) {
                    value = value.update(
                        buildExt {
                            add = (effect.value as? Array<Range<Decoration>>)
                            sort = true
                        }
                    )
                } else if (effect.`is`(filterMarks)) {
                    value = value.update(
                        buildExt {
                            filter = effect.value as? RangeFilter<Decoration>
                        }
                    )
                }
            }
            value
        }
        // Indicate that this field provides a set of decorations
        provide = { f ->
            EditorView.decorations.from(f)
        }
    }
)

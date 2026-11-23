package com.semiovert.app.core.designsystem

internal data class StitchedGlyph(
    val id: Long,
    val text: String,
    val start: Int,
    val end: Int,
    val entering: Boolean,
)

internal class StitchedGlyphTracker {
    private var previous: List<StitchedGlyph> = emptyList()
    private var initialized=false
    private var nextId=0L

    fun update(tokens: List<String>): List<StitchedGlyph> {
        var prefix=0
        while(prefix<minOf(previous.size,tokens.size)&&previous[prefix].text==tokens[prefix])prefix++
        var suffix=0
        while(suffix<minOf(previous.size,tokens.size)-prefix&&
            previous[previous.lastIndex-suffix].text==tokens[tokens.lastIndex-suffix])suffix++

        var offset=0
        val current=tokens.mapIndexed {index,token ->
            // An edit moves surviving glyphs without restarting their arrival animations.
            val old=when {
                index<prefix->previous[index]
                index>=tokens.size-suffix->previous[previous.size-tokens.size+index]
                else->null
            }
            val glyph=old?.copy(start=offset,end=offset+token.length)
                ?:StitchedGlyph(nextId++,token,offset,offset+token.length,entering=initialized)
            offset+=token.length
            glyph
        }
        previous=current
        initialized=true
        return current
    }
}

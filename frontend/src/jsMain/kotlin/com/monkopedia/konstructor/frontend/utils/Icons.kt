/*
 * Copyright 2022 Jason Monk
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
package com.monkopedia.konstructor.frontend.utils

import react.dom.svg.SVGAttributes
import react.dom.svg.StrokeLinecap.Companion.round
import react.dom.svg.StrokeLinejoin.Companion.miter
import web.svg.SVGPathElement

object Icons {

    fun SVGAttributes<SVGPathElement>.stlIcon() {
        fill = "#ffffff"
        stroke = "none"
        transform = "translate(-6,0)"
        d = "M 0 0 v 13 h 26 v -13 z" +
            "m 12.336232,7.7846048 q 0,1.0054134 -0.730248,1.5663283 -0.730248,0.5609149 " +
            "-1.9684939,0.5609149 -0.634998,0 -1.1747463,-0.09525 Q 7.9229956,9.7213486 " +
            "7.5631634,9.5520158 V 8.6418521 q 0.3809988,0.1693327 0.9419136,0.3069156 " +
            "0.5714982,0.1375829 1.1747463,0.1375829 0.8466637,0 1.2699957,-0.3280822 " +
            "0.433916,-0.3280823 0.433916,-0.8889972 0,-0.3704155 -0.15875,-0.6244147 " +
            "Q 11.066236,6.9908573 10.674653,6.7791913 10.293655,6.556942 9.6057402,6.3135261 " +
            "8.6426599,5.9642772 8.1452449,5.4562789 7.6584131,4.9482805 7.6584131,4.0698666 " +
            "q 0,-0.6032481 0.3069157,-1.0265801 0.3069156,-0.4339152 0.8466639,-0.6667478 " +
            "0.5503316,-0.2328326 1.2594123,-0.2328326 0.624415,0 1.142997,0.1164163 " +
            "0.518581,0.1164163 0.941913,0.3069157 l -0.296332,0.814914 Q 11.468401,3.2126193 " +
            "11.002736,3.096203 10.547654,2.9797867 10.050239,2.9797867 q -0.7090813,0 " +
            "-1.0689135,0.3069157 -0.3598322,0.2963324 -0.3598322,0.7937475 0,0.3809988 " +
            "0.1587495,0.634998 0.1587495,0.2539992 0.5185817,0.4550819 0.3598322,0.2010827 " +
            "0.9736635,0.4339153 0.666748,0.2434159 1.12183,0.5291649 0.465665,0.2751658 " +
            "0.698498,0.6667479 0.243416,0.3915821 0.243416,0.9842469 z" +
            "M 16.25205,9.806015 H 15.299553 V 3.0856197 H 12.939477 V 2.2495391 h 5.662066 " +
            "V 3.0856197 H 16.25205 Z" +
            "M 19.744537,9.806015 V 2.2495391 h 0.952497 V 8.959351 h 3.30199 v 0.846664 z"
    }

    fun SVGAttributes<SVGPathElement>.filledBlocks(opacity: String = "1") {
        fill = "#ffffff"
        stroke = "#ffffff"
        strokeWidth = .5
        strokeLinecap = round
        strokeLinejoin = miter
        strokeOpacity = opacity
        d =
            "M 3 11 h 5 a 1 1 90 0 1 1 1 v 5 a 1 1 90 0 1 -1 1 h-5 a 1 1 90 0 1 -1 -1 v-5 " +
            "a 1 1 90 0 1 1 -1 M 11 11 h 5 a 1,1 0 0 1 1,1 v 5 a 1,1 0 0 1 -1,1 h -5 " +
            "a 1,1 0 0 1 -1,-1 v -5 a 1,1 0 0 1 1,-1 z M 7 2 h 5 a 1,1 0 0 1 1,1 v 5 " +
            "a 1,1 0 0 1 -1,1 h -5 a 1,1 0 0 1 -1,-1 v -5 a 1,1 0 0 1 1,-1 z "
    }

    fun SVGAttributes<SVGPathElement>.blocks(opacity: String = "1") {
        fill = "none"
        stroke = "#ffffff"
        strokeWidth = .5
        strokeLinecap = round
        strokeLinejoin = miter
        strokeOpacity = opacity
        d =
            "M 5.5 17.5 h 5 a 1 1 90 0 1 1 1 v 5 a 1 1 90 0 1 -1 1 h-5 a 1 1 90 0 1 -1 -1 v-5 " +
            "a 1 1 90 0 1 1 -1 M 14 17.5 h 5 a 1,1 0 0 1 1,1 v 5 a 1,1 0 0 1 -1,1 h -5 " +
            "a 1,1 0 0 1 -1,-1 v -5 a 1,1 0 0 1 1,-1 z M 8.5 9.5 h 5 a 1,1 0 0 1 1,1 v 5 " +
            "a 1,1 0 0 1 -1,1 h -5 a 1,1 0 0 1 -1,-1 v -5 a 1,1 0 0 1 1,-1 z M12 1.5 h 5 " +
            "a 1,1 0 0 1 1,1 v 5 a 1,1 0 0 1 -1,1 h -5 a 1,1 0 0 1 -1,-1 v -5 " +
            "a 1,1 0 0 1 1,-1 z"
    }

    fun SVGAttributes<SVGPathElement>.blocksBorder() {
        fill = "none"
        stroke = "#ffffff"
        strokeWidth = .5
        strokeLinecap = round
        strokeLinejoin = miter
        strokeOpacity = "1"
        d =
            "M 5.5 17.5 h 1 a 1 1 90 0 0 1 -1 v-6 a 1,1 0 0 1 1,-1 h 1.5 " +
            "a 1 1 90 0 0 1 -1 v-6 a 1,1 0 0 1 1,-1 h 5 a 1,1 0 0 1 1,1 v 5 " +
            "a 1,1 0 0 1 -1,1 h -1.5 a 1 1 90 0 0 -1 1 v7 a 1 1 90 0 0 1 1 h 3.5 " +
            "a 1 1 90 0 1 1 1 v 5 a 1 1 90 0 1 -1 1 h-13.5 a 1 1 90 0 1 -1 -1 v-5 " +
            "a 1 1 90 0 1 1 -1"
    }
}

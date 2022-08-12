package com.monkopedia.konstructor.frontend.utils

import kotlinext.js.js

inline fun <T> buildExt(builder: T.() -> Unit): T {
    return (js {  } as T).also(builder)
}
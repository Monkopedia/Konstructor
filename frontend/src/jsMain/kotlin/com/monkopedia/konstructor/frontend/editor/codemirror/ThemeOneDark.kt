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
@file:JsModule("@codemirror/theme-one-dark")
@file:JsNonModule
package codemirror.themeonedark

/**
 * Extension to enable the One Dark theme (both the editor theme and the highlight style).
 */
external val oneDark: dynamic /* Extension */

/**
 * The editor theme styles for One Dark.
 */
external val oneDarkTheme: dynamic /* Extension */

/**
 * The highlighting style for code in the One Dark theme.
 */
external val oneDarkHighlightStyle: dynamic /*HighlightStyle*/

package com.monkopedia.konstructor.e2e

import com.microsoft.playwright.Page
import java.io.File
import java.nio.file.Paths
import org.junit.Test
import kotlin.test.assertTrue

class ComposeDebugTest : BaseE2eTest() {
    @Test
    fun inspectComposeDom() {
        loadApp()
        page.waitForTimeout(15000.0)

        // Check full HTML
        val html = page.content()
        System.err.println("=== HTML LENGTH: ${html.length} ===")
        System.err.println("=== Contains 'role': ${html.contains("role")} ===")
        System.err.println("=== Contains 'aria': ${html.contains("aria")} ===")
        System.err.println("=== Contains 'input': ${html.contains("input")} ===")
        System.err.println("=== Contains 'canvas': ${html.contains("canvas")} ===")

        // Screenshot
        val dir = File(System.getProperty("user.dir"), "build/screenshots")
        dir.mkdirs()
        page.screenshot(
            Page.ScreenshotOptions()
                .setPath(Paths.get(dir.absolutePath, "compose-empty-state.png"))
                .setFullPage(true)
        )

        // Check body children specifically
        val bodyChildren = page.evaluate("""() => {
            return Array.from(document.body.children).map(el =>
                el.tagName + ' id=' + (el.id || '') +
                ' class=' + (el.className || '').toString().substring(0, 80) +
                ' outerHTML=' + el.outerHTML.substring(0, 200)
            ).join('\n');
        }""") as String
        System.err.println("=== BODY CHILDREN ===")
        System.err.println(bodyChildren)

        // Dump DOM structure
        val domInfo = page.evaluate("""() => {
            const result = [];
            const walk = (el, depth) => {
                const tag = el.tagName || 'text';
                const role = el.getAttribute && el.getAttribute('role') || '';
                const ariaLabel = el.getAttribute && el.getAttribute('aria-label') || '';
                const testId = el.getAttribute && el.getAttribute('data-testid') || '';
                const classes = el.className || '';
                if (depth < 5) {
                    result.push('  '.repeat(depth) + tag +
                        (role ? ' role=' + role : '') +
                        (ariaLabel ? ' aria-label=' + ariaLabel : '') +
                        (testId ? ' data-testid=' + testId : '') +
                        (classes && typeof classes === 'string' ? ' class=' + classes.substring(0,50) : ''));
                }
                if (el.children) {
                    for (let i = 0; i < el.children.length && i < 20; i++) {
                        walk(el.children[i], depth + 1);
                    }
                }
            };
            walk(document.body, 0);
            return result.join('\n');
        }""") as String
        System.err.println("=== DOM STRUCTURE ===")
        System.err.println(domInfo)

        // Check for any input elements
        val inputs = page.querySelectorAll("input")
        System.err.println("Input elements: ${inputs.size}")

        // Check for any elements with role
        val roles = page.evaluate("""() => {
            return Array.from(document.querySelectorAll('[role]'))
                .map(el => el.tagName + ' role=' + el.getAttribute('role') + ' text=' + (el.textContent || '').substring(0, 50))
                .join('\\n');
        }""") as String
        System.err.println("=== ROLE ELEMENTS ===")
        System.err.println(roles)

        assertTrue(true)
    }
}

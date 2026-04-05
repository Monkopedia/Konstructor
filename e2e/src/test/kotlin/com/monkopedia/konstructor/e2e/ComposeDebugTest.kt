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

        // Check shadow DOM for a11y elements
        val shadowInfo = page.evaluate("""() => {
            const results = [];
            const walk = (el, depth) => {
                if (depth > 8) return;
                const tag = el.tagName || '#text';
                const role = el.getAttribute ? el.getAttribute('role') : '';
                const id = el.id || '';
                const ariaLabel = el.getAttribute ? el.getAttribute('aria-label') : '';
                const text = (el.innerText || '').substring(0, 50).replace(/\\n/g, ' ');
                const info = '  '.repeat(depth) + tag +
                    (id ? ' id=' + id : '') +
                    (role ? ' role=' + role : '') +
                    (ariaLabel ? ' aria=' + ariaLabel : '') +
                    (text && !el.children?.length ? ' text=' + text : '');
                results.push(info);
                if (el.shadowRoot) {
                    results.push('  '.repeat(depth+1) + '#shadow-root');
                    Array.from(el.shadowRoot.children).forEach(c => walk(c, depth+2));
                }
                if (el.children) {
                    Array.from(el.children).slice(0, 30).forEach(c => walk(c, depth+1));
                }
            };
            walk(document.body, 0);
            return results.join('\n');
        }""") as String
        System.err.println("=== SHADOW DOM STRUCTURE ===")
        System.err.println(shadowInfo)

        // Search for cmp_a11y_root anywhere in the DOM tree, including shadow roots
        val a11yInfo = page.evaluate("""() => {
            const results = [];
            function findA11y(root, path) {
                if (!root) return;
                // Check shadow root
                if (root.shadowRoot) {
                    results.push(path + ' -> #shadow-root');
                    findA11y(root.shadowRoot, path + '/#shadow');
                }
                // Check children
                const children = root.children || root.childNodes;
                if (children) {
                    for (let i = 0; i < children.length && i < 50; i++) {
                        const child = children[i];
                        if (!child.tagName) continue;
                        const id = child.id || '';
                        const tag = child.tagName;
                        const role = child.getAttribute ? child.getAttribute('role') : '';
                        const childPath = path + '/' + tag + (id ? '#' + id : '') + (role ? '[' + role + ']' : '');
                        results.push(childPath);
                        findA11y(child, childPath);
                    }
                }
            }
            findA11y(document.body, 'body');
            return results.join('\n');
        }""") as String
        System.err.println("=== FULL DOM TREE ===")
        System.err.println(a11yInfo)

        // Try Playwright shadow-piercing selectors
        val byRole = page.getByRole(com.microsoft.playwright.options.AriaRole.TEXTBOX).all()
        System.err.println("Textbox roles found: ${byRole.size}")
        val byText = page.getByText("Workspace").all()
        System.err.println("'Workspace' text found: ${byText.size}")

        assertTrue(true)
    }
}

const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const resources = path.resolve(__dirname, '../../main/resources');

test('pages load the local MathJax bundle instead of a CDN', () => {
    const templates = path.join(resources, 'templates');
    const pages = fs.readdirSync(templates).filter((name) => name.endsWith('.html'));
    let pagesWithLatex = 0;

    for (const page of pages) {
        const html = fs.readFileSync(path.join(templates, page), 'utf8');
        if (!html.includes('/js/latex-render.js')) {
            continue;
        }
        pagesWithLatex++;
        assert.ok(html.includes('/webjars/mathjax/3.2.2/es5/tex-mml-chtml.js'), page);
        assert.ok(!html.includes('cdn.jsdelivr.net/npm/mathjax'), page);
    }
    assert.ok(pagesWithLatex > 0);
});

test('renders after MathJax is ready and after dynamic text changes', async () => {
    const listeners = new Map();
    let observer;
    let typesetCount = 0;
    const window = {
        addEventListener(name, listener) { listeners.set(name, listener); },
        dispatchEvent(event) { listeners.get(event.type)?.(event); },
        requestAnimationFrame(callback) { callback(); }
    };
    const document = {
        body: {},
        addEventListener(name, listener) { listeners.set(name, listener); }
    };
    class MutationObserver {
        constructor(callback) { this.callback = callback; observer = this; }
        observe() {}
        disconnect() {}
    }
    class Event {
        constructor(type) { this.type = type; }
    }

    const context = { window, document, MutationObserver, Event, console };
    vm.runInNewContext(fs.readFileSync(path.join(resources, 'static/js/latex-render.js'), 'utf8'), context);
    context.MathJax = window.MathJax;
    window.MathJax.startup.defaultPageReady = () => Promise.resolve();
    window.MathJax.typesetPromise = () => {
        typesetCount++;
        return Promise.resolve();
    };

    await window.MathJax.startup.pageReady();
    await new Promise(setImmediate);
    assert.equal(typesetCount, 1);

    observer.callback([{ type: 'characterData' }]);
    await new Promise(setImmediate);
    assert.equal(typesetCount, 2);
});

test('normalizes escaped LaTeX delimiters before MathJax typesetting', async () => {
    const listeners = new Map();
    let observer;
    let typesetCount = 0;
    const textNode = { nodeValue: String.raw`Формула \\(x\\) и команда \frac{1}{2}`, parentElement: { tagName: 'div' } };
    let walked = false;
    const window = {
        addEventListener(name, listener) { listeners.set(name, listener); },
        dispatchEvent(event) { listeners.get(event.type)?.(event); },
        requestAnimationFrame(callback) { callback(); }
    };
    const document = {
        body: {},
        createTreeWalker() {
            return {
                nextNode() {
                    if (walked) return null;
                    walked = true;
                    return textNode;
                }
            };
        },
        addEventListener(name, listener) { listeners.set(name, listener); }
    };
    class MutationObserver {
        constructor(callback) { this.callback = callback; observer = this; }
        observe() {}
        disconnect() {}
    }
    class Event {
        constructor(type) { this.type = type; }
    }
    const NodeFilter = { SHOW_TEXT: 4, FILTER_ACCEPT: 1, FILTER_REJECT: 2 };

    const context = { window, document, MutationObserver, Event, NodeFilter, console };
    vm.runInNewContext(fs.readFileSync(path.join(resources, 'static/js/latex-render.js'), 'utf8'), context);
    context.MathJax = window.MathJax;
    window.MathJax.startup.defaultPageReady = () => Promise.resolve();
    window.MathJax.typesetPromise = () => {
        typesetCount++;
        return Promise.resolve();
    };

    await window.MathJax.startup.pageReady();
    await new Promise(setImmediate);
    assert.equal(typesetCount, 1);
    assert.equal(textNode.nodeValue, String.raw`Формула \(x\) и команда \frac{1}{2}`);
});

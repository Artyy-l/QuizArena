window.MathJax = {
    tex: {
        inlineMath: [['\\(', '\\)'], ['$', '$']],
        displayMath: [['\\[', '\\]'], ['$$', '$$']],
        processEscapes: true
    },
    options: {
        skipHtmlTags: ['script', 'noscript', 'style', 'textarea', 'pre', 'code']
    },
    startup: {
        typeset: false,
        pageReady: function () {
            return MathJax.startup.defaultPageReady().then(function () {
                window.dispatchEvent(new Event('mathjax-ready'));
            });
        }
    }
};

(function () {
    'use strict';

    var observer = null;
    var typesetting = false;
    var renderQueued = false;
    var renderRequestedBeforeMathJax = false;

    // В некоторых сгенерированных вопросах разделители экранированы (\\\\( ... \\\\)),
    // тогда как MathJax ожидает (\\( ... \\)). Нормализуем только слеши разделителей,
    // чтобы не менять команды LaTeX.
    function normalizeLatexDelimiters(root) {
        if (!root || typeof document.createTreeWalker !== 'function' || typeof NodeFilter === 'undefined') {
            return;
        }

        var walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT, {
            acceptNode: function (node) {
                var parent = node.parentElement;
                if (!parent || /^(SCRIPT|STYLE|TEXTAREA|PRE|CODE|MJX-CONTAINER)$/i.test(parent.tagName)) {
                    return NodeFilter.FILTER_REJECT;
                }
                return NodeFilter.FILTER_ACCEPT;
            }
        });
        var node;
        while ((node = walker.nextNode())) {
            var normalized = node.nodeValue.replace(/\\{2,}(?=[()[\]])/g, '\\');
            if (normalized !== node.nodeValue) {
                node.nodeValue = normalized;
            }
        }
    }

    function hasMathJax() {
        return window.MathJax && typeof window.MathJax.typesetPromise === 'function';
    }

    function observeBody() {
        if (!document.body || observer) {
            return;
        }

        observer = new MutationObserver(function (mutations) {
            if (typesetting) {
                return;
            }

            var hasRenderableChange = mutations.some(function (mutation) {
                return mutation.type === 'characterData'
                    || mutation.type === 'childList' && (mutation.addedNodes.length > 0 || mutation.removedNodes.length > 0);
            });

            if (hasRenderableChange) {
                queueRender();
            }
        });

        observer.observe(document.body, {
            subtree: true,
            childList: true,
            characterData: true
        });
    }

    function queueRender() {
        if (renderQueued) {
            return;
        }

        renderQueued = true;
        window.requestAnimationFrame(function () {
            renderQueued = false;
            renderLatex();
        });
    }

    function renderLatex() {
        if (!hasMathJax()) {
            renderRequestedBeforeMathJax = true;
            return;
        }

        if (typesetting || !document.body) {
            renderRequestedBeforeMathJax = true;
            return;
        }

        typesetting = true;
        renderRequestedBeforeMathJax = false;
        if (observer) {
            observer.disconnect();
            observer = null;
        }

        normalizeLatexDelimiters(document.body);

        window.MathJax.typesetPromise([document.body])
            .catch(function (error) {
                console.warn('Не удалось отрендерить LaTeX:', error);
            })
            .finally(function () {
                typesetting = false;
                observeBody();
                if (renderRequestedBeforeMathJax) {
                    queueRender();
                }
            });
    }

    window.renderLatex = renderLatex;

    window.addEventListener('mathjax-ready', function () {
        observeBody();
        queueRender();
    });

    document.addEventListener('DOMContentLoaded', function () {
        observeBody();
        queueRender();
    });
})();

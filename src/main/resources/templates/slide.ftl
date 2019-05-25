<!doctype html>
<html lang="en">
    <head>
        <meta charset="utf-8">
        <title>${title}</title>
        <meta name="apple-mobile-web-app-capable" content="yes">
        <meta name="apple-mobile-web-app-status-bar-style" content="black-translucent">
        <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
        <link rel="stylesheet" href="/static/reveal.js/css/reset.css">
        <link rel="stylesheet" href="/static/reveal.js/css/reveal.css">
        <!-- Themes: black, white, beige, blood, moon, simple, solarized, template, league, night, serif, sky -->
        <link rel="stylesheet" href="/static/reveal.js/css/theme/moon.css" id="theme">
        <!-- Theme used for syntax highlighting of code -->
        <link rel="stylesheet" href="/static/reveal.js/lib/css/monokai.css">
        <!--[if lt IE 9]>
        <script src="/static/reveal.js/lib/js/html5shiv.js"></script>
        <![endif]-->
        <script src="/static/js/jquery.min.js"></script>
    </head>
    <body>
        <div class="reveal">
            <!-- Any section element inside of this container is displayed as a slide -->
            <div id="slides" class="slides">
                <section>
                    <div>Loading Slides...</div>
                </section>
            </div>
        </div>
        <script src="/static/reveal.js/js/reveal.js"></script>
        <script type="text/javascript">
            function initSlide() {
                // More info https://github.com/hakimel/reveal.js#configuration
                Reveal.initialize({
                    controls: true,
                    progress: true,
                    history: true,
                    center: true,
                    hash: true,
                    loop: false,
                    controlsLayout: 'bottom-right',

                    transition: 'slide', // none/fade/slide/convex/concave/zoom

                    // More info https://github.com/hakimel/reveal.js#dependencies
                    dependencies: [
                        { src: '/static/reveal.js/plugin/markdown/marked.js', condition: function() { return !!document.querySelector( '[data-markdown]' ); } },
                        { src: '/static/reveal.js/plugin/markdown/markdown.js', condition: function() { return !!document.querySelector( '[data-markdown]' ); } },
                        { src: '/static/reveal.js/plugin/highlight/highlight.js', async: true, callback: function() { hljs.initHighlightingOnLoad(); } },
                        { src: '/static/reveal.js/plugin/search/search.js', async: true },
                        { src: '/static/reveal.js/plugin/zoom-js/zoom.js', async: true },
                        { src: '/static/reveal.js/plugin/notes/notes.js', async: true }
                    ]
                });
            }

            $(function () {
                // update title, select a slide to show, default to number 1
                $.get("slide.html", function(content) {
                            $("#slides").html(content);
                            initSlide();
                    }).fail(function() {
                            $(document).attr("title", "出错了!");
                            $("#slides").html("<div>出错了!</div>");
                    });
            });
        </script>
    </body>
</html>

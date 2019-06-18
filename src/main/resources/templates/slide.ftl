<!doctype html>
<html lang="en">
    <head>
        <meta charset="utf-8">
        <title>${title}</title>
        <meta name="apple-mobile-web-app-capable" content="yes">
        <meta name="apple-mobile-web-app-status-bar-style" content="black-translucent">
        <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
        <link rel="stylesheet" href="${webRootPath}/reveal.js/reset.css">
        <link rel="stylesheet" href="${webRootPath}/reveal.js/reveal.css">
        <!-- Themes: black, white, beige, blood, moon, simple, solarized, template, league, night, serif, sky -->
        <link rel="stylesheet" href="${webRootPath}/reveal.js/theme/${reveal.theme}.css" id="theme">
        <!-- Theme used for syntax highlighting of code -->
        <link rel="stylesheet" href="${webRootPath}/reveal.js/css/${reveal.highlight}.css">
        <!--[if lt IE 9]>
        <script src="${webRootPath}/reveal.js/js/html5shiv.js"></script>
        <![endif]-->
        <script src="${webRootPath}/jquery/jquery.min.js"></script>
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
        <script src="${webRootPath}/reveal.js/reveal.js"></script>
        <script type="text/javascript">
            function initSlide() {
                // More info https://github.com/hakimel/reveal.js#configuration
                Reveal.initialize({
                    controls: ${reveal.controls},
                    progress: ${reveal.progress},
                    history: ${reveal.history},
                    center: ${reveal.center},
                    hash: ${reveal.hash},
                    loop: ${reveal.loop},
                    controlsLayout: '${reveal.layout}',
                    transition: '${reveal.transition}',
                    dependencies: [
                        { src: '${webRootPath}/reveal.js/markdown/marked.js', condition: function() { return !!document.querySelector( '[data-markdown]' ); } },
                        { src: '${webRootPath}/reveal.js/markdown/markdown.js', condition: function() { return !!document.querySelector( '[data-markdown]' ); } },
                        { src: '${webRootPath}/reveal.js/highlight/highlight.js', async: true, callback: function() { hljs.initHighlightingOnLoad(); } },
                        { src: '${webRootPath}/reveal.js/search/search.js', async: true },
                        { src: '${webRootPath}/reveal.js/math/math.js', async: true },
                        { src: '${webRootPath}/reveal.js/zoom-js/zoom.js', async: true },
                        { src: '${webRootPath}/reveal.js/notes/notes.js', async: true }
                    ]
                });
            }

            $(function () {
                // update title, select a slide to show, default to number 1
                $.get("slide.html", function(content) {
                            $("#slides").html(content);
                            initSlide();
                    }).fail(function() {
                            $(document).attr("title", "Something is wrong");
                            $("#slides").html("<div>Something is wrong!</div>");
                    });
            });
        </script>
    </body>
</html>

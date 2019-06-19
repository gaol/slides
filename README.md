# Slides
Provides an easy way to produce HTML slides based on [reveal.js](https://github.com/hakimel/reveal.js/) project

## How to use
This can be used in several ways, most common use case can be running the docker container directly.

### Use the docker image

In case you have `slides` directory in your working directory, run the following command can start a
ready-to-use slides webapp for you.
> docker run --rm -p 8080:8080 -v \`pwd\`:/opt/app aoingl/slides-app

#### Example:

Current working directory structure:

    .
    ├── slides                      # this can be overriden
    │   └── free_talk               # This is the slide name
    │       ├── slide.html          # this is the main slide content. (reveal.js style)
    │       ├── slide.json          # this is the customized freemarker context used in the default slide template
    │       └── use_mouse.jpg       # any image can be refered as relative link
    └── slides_zip                  # any slides zip file can be stored here.
        └── my_talk.zip             # slide name in this case is: 'my_talk'


After running above command, the slide in `slides/free_talk` can be accessed using link:
> http://localhost:8080/slides/free_talk/

Any changes in the slides will take effect immediately.

Also, if the slides are packaged into a zip file, just like the `my_talk.zip` in this example,
it can be accessed using link:

> http://localhost:8080/slides/my_talk/

The `my_talk.zip` file structure is just like the `free_talk` directory:

    [🎩 lgao@lgao slides]$ unzip -l slides_zip/my_talk.zip 
    Archive:  slides_zip/my_talk.zip
      Length      Date    Time    Name
    ---------  ---------- -----   ----
        27360  06-19-2019 16:37   use_mouse.jpg
          141  06-19-2019 16:40   slide.html
           40  06-19-2019 16:40   slide.json
    ---------                     -------
        27541                     3 files



## How to build

> mvn clean install -Papp

Use the profile `app` to build the `slides-app.jar`

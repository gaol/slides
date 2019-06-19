###
# To build:
#  mvn clean install -Papp
#  docker build -t aoingl/slides-app .
# To run:
#   docker run --rm -p 8080:8080 -v `pwd`:/opt/app aoingl/slides-app
###

FROM java:8-jre

ENV VERTICLE_FILE slides-app.jar
ENV VERTICLE_HOME /usr/verticles

ENV SLIDES_ROOT_DIR slides
ENV SLIDES_ZIP_DIR  slides_zip
ENV SLIDES_PATH /slides


EXPOSE 8080

# Copy the jar to the container
COPY target/$VERTICLE_FILE $VERTICLE_HOME/

# working directory is the mount point to current host dir.
WORKDIR /opt/app/

ENTRYPOINT ["sh", "-c"]
CMD ["exec java -cp . -Dslides.path=$SLIDES_PATH -Dslides.root.dir=$SLIDES_ROOT_DIR -Dslides.zip.root.dir=$SLIDES_ZIP_DIR -jar $VERTICLE_HOME/$VERTICLE_FILE"]

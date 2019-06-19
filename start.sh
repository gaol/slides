#!/bin/bash

docker run --rm -p 8080:8080 -v `pwd`:/opt/app aoingl/slides-app


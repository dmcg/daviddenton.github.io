#!/bin/bash
docker build . -t jekyll-drafts
docker run -p 4000:4000 -v $(pwd):/site jekyll-drafts
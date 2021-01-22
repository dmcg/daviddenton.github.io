FROM bretfisher/jekyll-serve@sha256:701b6c4778cf85987b3693f7c6b4bf9b950f9973e6890a3e5ae6f5a7fde07f3a

CMD [ "bundle", "exec", "jekyll", "serve", "--force_polling", "-H", "0.0.0.0", "-P", "4000", "--drafts" ]
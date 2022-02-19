# DEPRECATED

Use https://github.com/abaplint/abaplint-sci-server instead

~~Running Javascript inside Java was really slow. Plus abaplint requires abapGit naming for it to work properly. Currently this project is not maintained, an alternative approach is needed for making abaplint work in ADT.~~

[![Codacy Badge](https://api.codacy.com/project/badge/grade/56f3e9fbccd54a43b29c5dcaab37ea41)](https://www.codacy.com/app/larshp/abaplint-eclipse)

# abaplint-eclipse

~~[abaplint](https://github.com/larshp/abaplint) plugin for ABAP in Eclipse~~

### Testing

~~1. Import project into Eclipse~~

~~2. Build abaplint and copy web/script/bundle.js to org.abaplint.eclipse/src/bundle/~~

~~3. Run project as Eclipse Application~~

~~4. Right click ABAP project -> Configure -> Enable abaplint~~

### Installing

1. Import all three projects into Eclipse
2. build project [abaplint](https://github.com/larshp/abaplint)
3. copy web/script/bundle.js from the abaplint project to org.abaplint.eclipse/src/bundle/
4. open site.xml in project abaplint.site and press build all
5. go in help/install new software and add an update site pointing to local directory abaplint.site
6. install the plugin and restart eclipse
7. Right click ABAP project -> Configure -> Enable abaplint

Note that the first few times it is slow, see http://pieroxy.net/blog/2015/06/29/node_js_vs_java_nashorn.html

![example](https://cloud.githubusercontent.com/assets/5888506/13034067/685d6724-d32b-11e5-883d-11a2906b359a.png)

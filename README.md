# Where Used Plugin

Enables webmasters to see where in the website a particular document is used.

## Build and install the module into local maven repository

    $ mvn clean install

## Running the demo locally

The demo project is located under demo/ folder. So, move to the demo/ folder to run it locally.

    $ cd demo
    $ mvn clean verify
    $ mvn -P cargo.run

After startup, access the CMS at http://localhost:8080/cms and go to a document under /content.  You will see a "where used" dropdown for the currently selected document.


## Installation 

In the root pom.xml add the property:

    <whereused.version>VERSION</whereused.version>

Add the following dependencies in the SITE module:

    <dependency>
        <groupId>com.bloomreach.cms.plugin</groupId>
        <artifactId>bloomreach-where-used-plugin-site</artifactId>
        <version>${whereused.version}</version>
    </dependency>

In the CMS pom.xml add the following dependencies:
    
    <dependency>
      <groupId>com.bloomreach.cms.plugin</groupId>
      <artifactId>bloomreach-where-used-plugin-cms</artifactId>
      <version>${whereused.version}</version>
    </dependency>
    
    <dependency>
      <groupId>com.bloomreach.cms.plugin</groupId>
      <artifactId>bloomreach-where-used-plugin-repository</artifactId>
      <version>${whereused.version}</version>
    </dependency>
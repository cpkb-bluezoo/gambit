# Gambit

A generic PDF parser with good performance based on NIO and a SAX/Expat-like handler mechanism. The application is notified of parsing events as **structured events** (typed callbacks), not raw bytes.

## Design

- **NIO-based I/O**  
  The parser reads from a `SeekableByteChannel` (e.g. `FileChannel`) using `ByteBuffer` and buffered reads. This supports local files and remote resources (e.g. HTTP byte ranges for linearized PDFs) without loading the whole file into memory.

- **Event-driven, handler-based API**  
  Parsing is push-based: as the parser recognises PDF constructs, it invokes callbacks on a `PDFHandler`. There is no built-in DOM; the application reacts to events and can stream or discard data as needed (SAX/Expat style).

- **Structured events**  
  Events are typed and semantic: `startObject`/`endObject`, `startDictionary`/`key`/`endDictionary`, `startArray`/`endArray`, `booleanValue`, `numberValue`, `stringValue`, `nameValue`, `objectReference`, `startStream`/`streamContent`/`endStream`, etc. A `PDFLocator` (SAX-style) provides byte offsets for diagnostics and positioning.

## Usage

**Push style** (SAX-like): the parser loads the document and traverses from the catalog, firing events for every referenced object in discovery order.

```java
PDFHandler handler = new MyPDFHandler();
PDFParser parser = new PDFParser(handler);

try (FileChannel channel = FileChannel.open(Paths.get("document.pdf"))) {
    parser.parse(channel);
}
```

**Pull style**: load the xref and trailer only, then request only the objects you need. Handlers can decide which references to follow (e.g. a catalog handler asks for the Pages object; a Pages handler asks for each Page).

```java
PDFParser parser = new PDFParser(noOpHandler);  // or a handler that logs refs

try (FileChannel channel = FileChannel.open(Paths.get("document.pdf"))) {
    parser.load(channel);
    ObjectId catalogId = parser.getCatalogId();
    parser.parseObject(catalogId, catalogHandler);  // catalogHandler sees /Pages 3 0 R
    // catalogHandler then calls parser.parseObject(pagesId, pagesHandler);
    // pagesHandler sees /Kids [2 0 R] and calls parser.parseObject(2 0 R, pageHandler); etc.
}
```

Implement `PDFHandler` to receive the events you care about. Optional `PDFContentHandler`, `OpenTypeHandler`, and `CMapHandler` can be set for content-stream, font, and CMap parsing.

## Dependency: Gonzalez (XML parsing)

XMP metadata stream parsing requires [Gonzalez](https://github.com/bluezoo/gonzalez) (`org.bluezoo:gonzalez`) for XML parsing.

- **Maven:** The `pom.xml` declares Gonzalez as a required dependency. Configure GitHub Packages in your Maven settings (see [GitHub docs](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-apache-maven-registry)) and set the repository owner if needed (e.g. `GITHUB_PACKAGES_OWNER` or edit the repository URL in `pom.xml`).
- **Ant:** Put the Gonzalez JAR in the `lib/` directory so it is on the compile classpath. Download from GitHub Packages or build Gonzalez from source and copy the JAR into `lib/`. The build requires it for XMP support.

## Status

- PDF object model parsing (numbers, strings, names, arrays, dictionaries, indirect refs, streams).
- Cross-reference handling (legacy and XRef streams; incremental updates).
- Stream decoding pipeline (FlateDecode, LZW, ASCIIHex, ASCII85, RunLength) and chunked delivery.
- Content stream and OpenType parsing hooks.
- Object streams (PDF 1.5+ compressed objects); decoded via filter pipeline and cached for resolution.
- XRef streams decoded via the filter pipeline (FlateDecode, etc.); indirect /Length supported.
- CMap parsing (e.g. ToUnicode) via CMapHandler and CMapParser (codespacerange, bfchar, bfrange).
- **TODO:** Metadata/XMP and other stream-type parsers.

## License

GNU LGPL v3+.


-- Chris Burdess
const icons = {
    // these are from https://octicons.github.com

    "key":
    {"vb": "0 0 14 16",
     "d": "M12.83 2.17C12.08 1.42 11.14 1.03 10 1c-1.13.03-2.08.42-2.83 " +
     "1.17S6.04 3.86 6.01 5c0 .3.03.59.09.89L0 12v1l1 " +
     "1h2l1-1v-1h1v-1h1v-1h2l1.09-1.11c.3.08.59.11.91.11 " +
     "1.14-.03 2.08-.42 2.83-1.17S13.97 6.14 " +
     "14 5c-.03-1.14-.42-2.08-1.17-2.83zM11 5.38c-.77 0-1.38-.61-1.38-1.38 " +
     "0-.77.61-1.38 1.38-1.38.77 0 1.38.61 1.38 1.38 0 .77-.61 1.38-1.38 " +
     "1.38z"},

    "pencil":
    {"vb": "0 0 14 16",
     "d": "M0 12v3h3l8-8-3-3-8 8zm3 2H1v-2h1v1h1v1zm10.3-9.3L12 6 " +
     "9 3l1.3-1.3a.996.996 0 0 1 1.41 0l1.59 1.59c.39.39.39 1.02 0 1.41z"},

    "download":
    {"vb": "0 0 16 16",
     "d": "M9 12h2l-3 3-3-3h2V7h2v5zm3-8c0-.44-.91-3-4.5-3C5.08 1 3 " +
     "2.92 3 5 1.02 5 0 6.52 0 8c0 1.53 1 3 3 3h3V9.7H3C1.38 9.7 1.3 " +
     "8.28 1.3 8c0-.17.05-1.7 1.7-1.7h1.3V5c0-1.39 1.56-2.7 3.2-2.7 " +
     "2.55 0 3.13 1.55 3.2 1.8v1.2H12c.81 0 2.7.22 2.7 2.2 0 2.09-2.25 " +
     "2.2-2.7 2.2h-2V11h2c2.08 0 4-1.16 4-3.5C16 5.06 14.08 4 12 4z"},

    "trash":
    {"vb": "0 0 12 16",
     "d": "M11 2H9c0-.55-.45-1-1-1H5c-.55 0-1 .45-1 1H2c-.55 0-1 .45-1 " +
     "1v1c0 .55.45 1 1 1v9c0 .55.45 1 1 1h7c.55 0 1-.45 1-1V5c.55 0 1-.45 " +
     "1-1V3c0-.55-.45-1-1-1zm-1 " +
     "12H3V5h1v8h1V5h1v8h1V5h1v8h1V5h1v9zm1-10H2V3h9v1z"},

    "tag":
    {"vb": "0 0 15 16",
     "d": "M7.73 1.73C7.26 1.26 6.62 1 5.96 1H3.5C2.13 1 1 2.13 1 " +
     "3.5v2.47c0 .66.27 1.3.73 1.77l6.06 6.06c.39.39 1.02.39 1.41 " +
     "0l4.59-4.59a.996.996 0 0 0 0-1.41L7.73 1.73zM2.38 " +
     "7.09c-.31-.3-.47-.7-.47-1.13V3.5c0-.88.72-1.59 1.59-1.59h2.47c.42 " +
     "0 .83.16 1.13.47l6.14 6.13-4.73 4.73-6.13-6.15zM3.01 3h2v2H3V3h.01z"},

    "no":
    {"vb": "0 0 14 16",
     "d": "M7 1C3.14 1 0 4.14 0 8s3.14 7 7 7 7-3.14 7-7-3.14-7-7-7zm0 " +
     "1.3c1.3 0 2.5.44 3.47 1.17l-8 8A5.755 5.755 0 0 1 1.3 8c0-3.14 " +
     "2.56-5.7 5.7-5.7zm0 11.41c-1.3 0-2.5-.44-3.47-1.17l8-8c.73.97 1.17 " +
     "2.17 1.17 3.47 0 3.14-2.56 5.7-5.7 5.7z"},

    "arrow-left":
    {"vb": "0 0 10 16",
     "d": "M6 3L0 8l6 5v-3h4V6H6V3z"},

    "arrow-right":
    {"vb": "0 0 10 16",
     "d": "M10 8L4 3v3H0v4h4v3l6-5z"},

    "location":
    {"vb": "0 0 12 16",
     "d": "M6 0C2.69 0 0 2.5 0 5.5 0 10.02 6 16 6 16s6-5.98 6-10.5C12 " +
     "2.5 9.31 0 6 0zm0 14.55C4.14 12.52 1 8.44 1 5.5 1 3.02 3.25 1 6 " +
     "1c1.34 0 2.61.48 3.56 1.36.92.86 1.44 1.97 1.44 3.14 0 2.94-3.14 " +
     "7.02-5 9.05zM8 5.5c0 1.11-.89 2-2 2-1.11 0-2-.89-2-2 0-1.11.89-2 2-2 " +
     "1.11 0 2 .89 2 2z"},

    "person":
    {"vb": "0 0 12 16",
     "d": "M12 14.002a.998.998 0 0 1-.998.998H1.001A1 1 0 0 1 0 " +
     "13.999V13c0-2.633 4-4 4-4s.229-.409 0-1c-.841-.62-.944-1.59-1-4 " +
     ".173-2.413 1.867-3 3-3s2.827.586 3 3c-.056 2.41-.159 3.38-1 4-.229.59 " +
     "0 1 0 1s4 1.367 4 4v1.002z"},

    "upload":
    {"vb": "0 0 16 16",
     "d": "M7 9H5l3-3 3 3H9v5H7V9zm5-4c0-.44-.91-3-4.5-3C5.08 2 3 3.92 " +
     "3 6 1.02 6 0 7.52 0 9c0 1.53 1 3 3 3h3v-1.3H3c-1.62 " +
     "0-1.7-1.42-1.7-1.7 0-.17.05-1.7 1.7-1.7h1.3V6c0-1.39 1.56-2.7 " +
     "3.2-2.7 2.55 0 3.13 1.55 3.2 1.8v1.2H12c.81 0 2.7.22 2.7 2.2 0 " +
     "2.09-2.25 2.2-2.7 2.2h-2V12h2c2.08 0 4-1.16 4-3.5C16 6.06 14.08 5 " +
     "12 5z"},

     "x":
     {"vb": "0 0 12 16",
      "d": "M7.48 8l3.75 3.75-1.48 1.48L6 9.48l-3.75 " +
      "3.75-1.48-1.48L4.52 8 .77 4.25l1.48-1.48L6 6.52l3.75-3.75 " +
      "1.48 1.48L7.48 8z"},

    "check":
    {"vb": "0 0 12 16",
     "d": "M12 5l-8 8-4-4 1.5-1.5L4 10l6.5-6.5L12 5z"},

    "triangledown":
    {"vb": "0 0 12 16",
     "d": "M0 5l6 6 6-6H0z"},

    "triangleup":
    {"vb": "0 0 12 16",
     "d": "M12 11L6 5l-6 6h12z"},
};

const SVG_NS = "http://www.w3.org/2000/svg";

// This returns an 'Element' object which jquery will accept in places
// like '.append()', etc.  Helping hint: if you want the SVG HTML as a
// string (such as to paste inside a template literal), you can get it
// like so: getIcon("which").outerHTML

export function getIcon(which, params) {
    if (params === undefined) { params = {}; }
    if (params.width === undefined) { params.width = 24; }
    if (params.height === undefined) { params.height = 24; }
    if (icons[which] === undefined) { alert("icons has a sad"); }
    // note that you can't do any of this with jquery because it can't
    // deal with namespaces or xhtml or something
    let svg = document.createElementNS(SVG_NS, "svg");
    let path = document.createElementNS(SVG_NS, "path");
    svg.setAttribute("viewBox", icons[which].vb);
    svg.setAttribute("width", params.width);
    svg.setAttribute("height", params.height);
    svg.setAttribute("fill", "currentColor");
    path.setAttribute("fill-rule", "evenodd");
    path.setAttribute("d", icons[which].d);
    svg.appendChild(path);
    return svg;
}

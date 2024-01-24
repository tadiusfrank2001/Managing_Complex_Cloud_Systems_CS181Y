import { getIcon } from "./icons.js";

// create a horizontal row with the given text "label" at 25% width, and
// an <input> of type "type" at 75% width.
//
// if "wide" is true, then the input is a textarea at 100% width.

export function formgrp(id, type, label, val, wide) {
    if (val === undefined) val = "";
    let d = $("<div>", {"class": "row mb-2"});
    if (wide === undefined) {
	d.append($("<div>", {"class": "col-2 text-right py-1"})
		 .append(label))
	    .append($("<div>", {"class": "col-10"})
		    .append($("<input>", {"id": id,
					  "class": "form-control",
					  "type": type,
					  "value": val})));
    } else {
	d.append($("<div>", {"class": "col"})
		 .append($("<textarea>", {"id": id,
					  "class": "form-control",
					  "placeholder": label})
			 .text(val)));
    }
    return d;
}

// create a "row" containing two text inputs with labels, in the
// proportions 2-4-2-4.  (currently unused)
/*
export function dblFormGrp(json, label1, field1, label2, field2)
{
    let d = $("<div>", {"class": "row mb-2"});
    $("<div>", {"class": "col-2 text-right py-1"}).text(label1).appendTo(d);
    $("<div>", {"class": "col-4"})
        .append($("<input>", {"id": "inp" + field1,
                              "class": "form-control",
                              "type": "text",
                              "value": json[field1]}))
        .appendTo(d);
    $("<div>", {"class": "col-2 text-right py-1"}).text(label2).appendTo(d);
    $("<div>", {"class": "col-4"})
        .append($("<input>", {"id": "inp" + field2,
                              "class": "form-control",
                              "type": "text",
                              "value": json[field2]}))
        .appendTo(d);
    return d;
}
*/

// create a "row" div containing both a drop down select named "inpXXX"
// and a free-form text box named "freXXX" for the type of input where
// it is usually convenient to reuse a recent value, but you have to be
// able to create a new one too.

export function selGrp(field, label, placeholder) {
    let sel = $("<select>", {"id": "inp" + field, "class": "custom-select"});
    let fre = $("<input>", {"id": "fre" + field, "class": "form-control",
			    "placeholder": placeholder});
    return $("<div>", {"class": "row mb-2"})
	.append($("<div>", {"class": "col-2 text-right"})
		.append(label))
	.append($("<div>", {"class": "col-10 input-group"})
		.append(sel)
		.append(fre));
}

// create a "row" div that has a timestamp and timezone input.  The json
// object needs to have the "ts" and "tz" attributes.

export function timegrp(json) {
    return $("<div>", {"class": "row mb-2"})
	.append($("<div>", {"class": "col-2 text-right py-1"})
                .text("Timestamp"))
	.append($("<div>", {"class": "col-10 input-group"})
		.append($("<input>", {"id": "inpts",
				      "class": "form-control",
				      "type": "text",
				      "value": json.ts}))
                .append($("<select>", {"id": "inptz",
                                       "class": "custom-select"}))
		.append($("<input>", {"id": "fretz",
				      "class": "form-control",
				      "type": "text",
                                      "placeholder": "or a new timezone",
				      "value": json.tz})));
}

export function headerrow(t) {
    return $("<div>", {"class": "row my-3"})
	.append($("<div>", {"class": "col-2"}))
	.append($("<div>", {"class": "col-10"}).text(t));
}

// given prefix XXX, creates this:
//
// +--------------------------------------------------------+
// | col-2     | col-6 #areaXXX            | col-4 #freXXX  | \
// |     label | area where you can click  | text entry box |  } row
// |           | stuff                     |                | /
// +-----------+---------------------------+----------------+
//
// When you have a bag of stuff to draw in the clickable area, use
// PopulateTagGrp().

export function TagGrp(label, prefix, clickhandler) {
    return $("<div>", {"class": "row mb-2"})
        .append($("<div>", {"class": "col-2 text-right"}).append(label))
        .append($("<div>", {"id": "area" + prefix, "class": "col-6 h5"})
                .on("click", clickhandler))
        .append($("<div>", {"class": "col-4 input-group"})
                .append($("<input>",
                          {"id": "fre" + prefix,
                           "class": "form-control",
                           "placeholder": "or add any " + prefix})));
}

// draw a bunch of badges inside a TagEditGrp.  json[prefix] needs to
// be an array of objects such as:
//   {id: 123,
//    txt: "blah blah",
//    [varies: true,]
//    [act: true,]
//   }
// The combinations of "varies" and "act" determine what class is
// applied and therefore what color it turns out.

export function PopulateTagGrp(json, prefix) {

    let tagdiv = $("#area" + prefix).empty();
    if (json[prefix] === undefined) return;
    for (let i of json[prefix]) {
        let newtag = $("<a>", {"id": `tag_${prefix}_${i.id}`,
                               "class": "badge mr-2 mb-2",
                               "href": "#"})
            .text(i.txt);
        if (i.newval === true) {
            newtag.addClass("badge-success");
        } else if (i.newval === false) {
            newtag.addClass("badge-secondary");
        } else if (i.varies === true) {
            newtag.addClass("badge-warning");
        } else if (i.act) {
            newtag.addClass("badge-info");
        } else {
            newtag.addClass("badge-secondary");
        }
        tagdiv.append(newtag);
    }

}

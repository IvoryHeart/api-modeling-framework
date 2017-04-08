import * as joint from "jointjs";
var Rect = joint.shapes.basic.Rect;
var Link = joint.dia.Link;
var Graph = joint.dia.Graph;
var Paper = joint.dia.Paper;
import * as utils from "../utils";
const CHAR_SIZE = 10;
const DEFAULT_DOMAIN_COLOR = "wheat";
const SELECTED_STROKE_COLOR = "red";
const COLORS = {
    "encodes": "wheat",
    "declares": "lightpink",
    "references": "mediumseagreen",
    "unit": "azure",
    "domain": "beige",
    "declaration": "lavenderblush"
};
export class Diagram {
    constructor(selectedId, level, handler) {
        this.selectedId = selectedId;
        this.level = level;
        this.handler = handler;
        this.scaleX = 1;
        this.scaleY = 1;
    }
    process(elements) {
        this.nodes = {};
        this.links = [];
        this.elements = elements;
        this.elements.forEach(element => {
            if (element.kind === "Fragment") {
                this.processFragmentNode(element);
            }
            else if (element.kind === "Module") {
                this.processModuleNode(element);
            }
            else if (element.kind === "Document") {
                this.processDocumentNode(element);
            }
        });
        this.elements.forEach(element => {
            this.processReferences(element);
        });
    }
    render(div, cb) {
        setTimeout(() => {
            const diagContainer = document.getElementById(div);
            if (diagContainer != null) {
                let classes = [];
                for (let p in this.nodes) {
                    classes.push(this.nodes[p]);
                }
                let cells = (classes).concat(this.links);
                let acc = {};
                cells.forEach(c => acc[c.id] = true);
                const finalCells = cells.filter(c => {
                    return (c.attributes.source == null) || (acc[c.attributes.source.id] && acc[c.attributes.target.id]);
                });
                // const finalCells = cells;
                if (joint.layout != null) {
                    joint.layout.DirectedGraph.layout(finalCells, {
                        marginX: 50,
                        marginY: 50,
                        nodeSep: 100,
                        edgeSep: 100,
                        // clusterPadding: { top: 30, left: 10, right: 10, bottom: 10 },
                        rankDir: "LR"
                    });
                }
                const maxX = finalCells
                    .map(c => c['attributes'].position ? (c['attributes'].position.x + c['attributes'].size.width) : 0)
                    .sort((a, b) => {
                    if (a > b) {
                        return -1;
                    }
                    else if (a < b) {
                        return 1;
                    }
                    else {
                        return 0;
                    }
                })[0];
                const maxY = finalCells
                    .map(c => c['attributes'].position ? (c['attributes'].position.y + c['attributes'].size.height) : 0)
                    .sort((a, b) => {
                    if (a > b) {
                        return -1;
                    }
                    else if (a < b) {
                        return 1;
                    }
                    else {
                        return 0;
                    }
                })[0];
                finalCells.map(c => c['attributes'].position ? c['attributes'].position.x : 0);
                const graph = new Graph();
                let width = maxX + 100;
                let height = maxY + 100;
                if (diagContainer != null) {
                    diagContainer.innerHTML = "";
                    let minWidth = diagContainer.clientWidth;
                    // let minHeight = diagContainer.clientHeight;
                    let minHeight = window.innerHeight - 300;
                    const options = {
                        el: diagContainer,
                        width: (minWidth > width ? minWidth : width),
                        height: (minHeight > height ? minHeight : height),
                        gridSize: 1,
                        highlighting: false
                    };
                    options["model"] = graph;
                    this.paper = new Paper(options);
                    this.paper.on("cell:pointerdown", (cellView, evt, x, y) => {
                        const nodeId = cellView.model.attributes.attrs.nodeId;
                        const unit = cellView.model.attributes.attrs.unit;
                        //console.log(cellView);
                        //console.log(nodeId);
                        this.handler(nodeId, unit);
                    });
                    graph.addCells(finalCells);
                    //this.paper.fitToContent();
                    //this.resetZoom();
                    let zoomx = 1;
                    let zoomy = 1;
                    if (minWidth < width) {
                        zoomx = minWidth / width;
                    }
                    if (minHeight < height) {
                        zoomy = minHeight / height;
                    }
                    let zoom = zoomy < zoomx ? zoomy : zoomx;
                    this.paperScale(zoom, zoom);
                    if (cb) {
                        cb();
                    }
                    else {
                    }
                    return true;
                }
            }
        }, 100);
    }
    paperScale(sx, sy) {
        this.scaleX = sx;
        this.scaleY = sy;
        this.paper.scale(sx, sy);
    }
    zoomOut() {
        this.scaleX -= 0.05;
        this.scaleY -= 0.05;
        this.paperScale(this.scaleX, this.scaleY);
    }
    zoomIn() {
        this.scaleX += 0.05;
        this.scaleY += 0.05;
        this.paperScale(this.scaleX, this.scaleY);
    }
    resetZoom() {
        this.scaleX = 1;
        this.scaleY = 1;
        this.paperScale(this.scaleX, this.scaleY);
    }
    processFragmentNode(element) {
        this.makeNode(element, "unit", element);
        if (element.encodes != null && this.level !== "files") {
            const encodes = element.encodes;
            const encoded = encodes.domain ? encodes.domain.root : undefined;
            if (encoded && this.level === "domain") {
                this.processDomainElement(element.id, encodes.domain ? encodes.domain.root : undefined);
            }
            else if (this.level === "document") {
                this.makeNode(encodes, "domain", encodes);
                this.makeLink(element.id, encodes.id, "encodes");
            }
        }
    }
    processModuleNode(element) {
        this.makeNode(element, "unit", element);
        if (element.declares != null && this.level !== "files") {
            element.declares.forEach(declaration => {
                if (this.nodes[declaration.id] == null) {
                    this.makeNode(declaration, "declaration", declaration);
                }
                this.makeLink(element.id, declaration.id, "declares");
            });
        }
    }
    processDocumentNode(document) {
        this.makeNode(document, "unit", document);
        // first declarations to avoid refs in the domain level pointing
        // to declarations not added yet
        if (document.declares != null && this.level !== "files") {
            document.declares.forEach(declaration => {
                if (this.nodes[declaration.id] == null) {
                    this.makeNode(declaration, "declaration", declaration);
                }
                this.makeLink(document.id, declaration.id, "declares");
            });
        }
        if (document.encodes != null && this.level !== "files") {
            const encodes = document.encodes;
            const encoded = encodes.domain ? encodes.domain.root : undefined;
            if (encoded && this.level === "domain") {
                this.processDomainElement(document.id, encodes.domain ? encodes.domain.root : undefined);
            }
            else {
                this.makeNode(encodes, "domain", encodes);
                this.makeLink(document.id, encodes.id, "encodes");
            }
        }
    }
    processDomainElement(parentId, element) {
        if (element) {
            const domainKind = element.kind;
            switch (domainKind) {
                case "APIDocumentation": {
                    //console.log("Processing APIDomain in graph " + element.id);
                    this.makeNode(element, "domain", element);
                    this.makeLink(parentId, element.id, "encodes");
                    (element.endpoints || []).forEach(endpoint => {
                        this.processDomainElement(element.id, endpoint);
                    });
                    break;
                }
                case "EndPoint": {
                    //console.log("Processing EndPoint in graph " + element.id);
                    this.makeNode(element, "domain", element);
                    this.makeLink(parentId, element.id, "endpoint");
                    (element.operations || []).forEach(operation => {
                        this.processDomainElement(element.id, operation);
                    });
                    break;
                }
                case "Operation": {
                    //console.log("Processing Operation in graph " + element.id);
                    this.makeNode({ id: element.id, label: element.method }, "domain", element);
                    this.makeLink(parentId, element.id, "supportedOperation");
                    (element.requests || []).forEach(request => {
                        this.processDomainElement(element.id, request);
                    });
                    (element.responses || []).forEach(response => {
                        this.processDomainElement(element.id, response);
                    });
                    break;
                }
                case "Response": {
                    //console.log("Processing Response in graph " + element.id);
                    this.makeNode({ id: element.id, label: element.status }, "domain", element);
                    this.makeLink(parentId, element.id, "returns");
                    (element.payloads || []).forEach(payload => {
                        this.processDomainElement(element.id, payload);
                    });
                    break;
                }
                case "Request": {
                    //console.log("Processing Request in graph " + element.id);
                    this.makeNode({ id: element.id, label: "request" }, "domain", element);
                    this.makeLink(parentId, element.id, "expects");
                    (element.payloads || []).forEach(payload => {
                        this.processDomainElement(element.id, payload);
                    });
                    break;
                }
                case "Payload": {
                    //console.log("Processing Payload in graph " + element.id);
                    this.makeNode({ id: element.id, label: element.mediaType || "*/*" }, "domain", element);
                    this.makeLink(parentId, element.id, "payload");
                    this.processDomainElement(element.id, element.schema);
                    break;
                }
                case "Schema": {
                    //console.log("Processing Schema in graph " + element.id);
                    this.makeNode(element, "domain", element);
                    this.makeLink(parentId, element.id, "schema");
                    break;
                }
                case "Include": {
                    //console.log("Processing Schema in graph " + parentId + " <-> " + element.id + " <-> " + (element as IncludeRelationship).target);
                    this.makeNode(element, "relationship", element);
                    this.makeLink(parentId, element.id, "schema");
                    this.makeLink(element.id, element.target, "includes");
                    break;
                }
                default: {
                    this.makeNode(element, "domain", element);
                    break;
                }
            }
        }
        else {
            return undefined;
        }
    }
    processReferences(element) {
        if (element.references) {
            element.references.forEach(ref => {
                const reference = ref;
                if (reference.id && this.nodes[reference.id] != null) {
                    this.makeLink(element.id, reference.id, "references");
                }
            });
        }
    }
    makeNode(node, kind, unit) {
        const label = node.label != null ? node.label : utils.label(node.id);
        if (this.nodes[node.id] == null) {
            this.nodes[node.id] = new Rect({
                attrs: {
                    /*
                    rect: {
                        fill: COLORS[kind],
                        stroke: node.id === this.selectedId ? SELECTED_STROKE_COLOR : "black",
                        "stroke-width": node.id === this.selectedId ? "3" : "1"
                    },
                    */
                    text: {
                        text: label,
                        fill: "black"
                    }
                },
                position: {
                    x: 0,
                    y: 0
                },
                size: {
                    width: label.length * CHAR_SIZE,
                    height: 30
                }
            });
        }
    }
    makeLink(sourceId, targetId, label) {
        if (this.nodes[sourceId] && this.nodes[targetId]) {
            //console.log("GENERATING LINK FROM " + this.nodes[sourceId].id + " TO " + this.nodes[targetId].id);
            this.links.push(new Link({
                source: { id: this.nodes[sourceId].id },
                target: { id: this.nodes[targetId].id },
                attrs: {
                    ".marker-target": {
                        d: "M 10 0 L 0 5 L 10 10 z",
                        fill: COLORS[label] || DEFAULT_DOMAIN_COLOR,
                        stroke: COLORS[label] || DEFAULT_DOMAIN_COLOR
                    },
                    ".connection": { stroke: COLORS[label] || DEFAULT_DOMAIN_COLOR }
                },
                labels: [{
                        position: 0.5,
                        attrs: {
                            text: {
                                text: label
                            }
                        }
                    }]
            }));
        }
    }
}
//# sourceMappingURL=diagram.js.map
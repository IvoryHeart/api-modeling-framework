/**
 * Created by antoniogarrote on 10/05/2017.
 */

import {SH_NS, Shape, SHAPES_NS} from "./Shape";

export class JSONSchemaShape extends Shape {

    public getSchemaRaw(): string | undefined {
        return this.getValue<string>(`${SHAPES_NS}schemaRaw`);
    }

    public setSchemaRaw(schema: string | null) {
        this.setValue(`${SHAPES_NS}schemaRaw`, schema);
    }

    public static build(id: string): JSONSchemaShape {
        return new JSONSchemaShape({"@id": id, "@type": [`${SH_NS}Shape`, `${SHAPES_NS}JSONSchema`]})
    }
}
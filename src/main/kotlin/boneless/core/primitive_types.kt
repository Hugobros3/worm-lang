package boneless.type

enum class PrimitiveTypeEnum(val size: Int) {
    Bool(1),
    /*
    U8(1),
    U16(2),
    U32(4),
    U64(8),*/

    // Not needed for now
    //I8(1),
    //I16(2),
    I32(4),
    I64(8),

    F32(4),
    // F64(8)
    ;
}
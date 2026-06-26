package io.cuttlefish.linking

import kotlinx.serialization.*

@Serializable
data class Header(
    val fileName: String,
    val sectionSize: UShort,
    val symbolCount: UShort,
    val relocationCount: UShort,
)

@Serializable
data class SymbolTable(
    val name: String, val type: SymbolType, val offset: UShort
)

@Serializable
data class ObjectFile(
    val header: Header,
    val payload: List<UShort>,
    val symbolTables: List<SymbolTable>,
    val relocationTable: List<RelocationTable>
)

@Serializable
data class RelocationTable(
    val offset: UShort,
    val name: String,
    val type: RelocationType,
)


@Serializable
enum class SymbolType { Export, Import }

@Serializable
enum class RelocationType { ABS_16, ABS_LUI, ABS_LLI, REL_7 }
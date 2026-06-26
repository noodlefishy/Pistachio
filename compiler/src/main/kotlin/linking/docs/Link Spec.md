Designing a linker for a custom processor like the RiSC-16 requires shifting from emitting absolute machine code immediately to emitting a relocatable intermediate representation—an **Object File**—and then running a linker utility to merge those files into a final executable binary [docs/1. Hardware Specifications.md].

The following specification outlines the requirements, data structures, and algorithms to implement a two-pass static linker.

---

### 1. The Relocatable Object File Specification

To link multiple files, your compiler/assembler must generate intermediate Object Files (`.obj` or `.o`) rather than raw machine code. Each object file should contain four major sections:

#### A. Header
* **Section Sizes**: Length (in words) of the compiled instruction/data segment.
* **Symbol Count**: Number of entries in the Export/Import Symbol tables.
* **Relocation Count**: Number of instructions requiring address patching.

#### B. Compiled Code & Data Block (Tentative Payload)
* The raw 16-bit instructions and data words compiled as if this file started at address `0x0000`.
* Instructions that reference external symbols (or local labels whose final addresses are not yet known) use temporary values (like `0`) in their immediate fields.

#### C. The Symbol Table
A list of symbols defined in or referenced by this file. Each entry contains:
* **Symbol Name**: (e.g., `math_multiply` or `global_counter`).
* **Type**:
    * `EXPORT` (Global): The symbol is defined in this file and can be called by other files (e.g., a function label).
    * `IMPORT` (External): The symbol is used in this file but defined in a different file.
* **Offset**: If `EXPORT`, the relative address word-offset from the start of this file’s payload.

#### D. The Relocation Table
A list of instructions within the payload that cannot be finalized until all files are merged. Each entry contains:
* **Instruction Offset**: The word address within this file's payload that needs to be modified.
* **Symbol Name**: The name of the symbol that this instruction depends on.
* **Relocation Type**:
    * `ABS_16`: The target symbol's absolute 16-bit address needs to be patched in.
    * `ABS_LUI` / `ABS_LLI`: The target symbol's upper 10-bits or lower 6-bits must be patched in (for the pseudo-instruction `movi` which expands to `lui` + `addi`) [docs/4. Programing Specifications.md].
    * `REL_7`: A PC-relative branch. The immediate field must be patched with a signed 7-bit relative offset [docs/1. Hardware Specifications.md].

---

### 2. The Linking Process (Two-Pass Algorithm)

The linker takes a list of relocatable object files and a target **Base Address** (e.g., `0x3000` for userland programs) and runs a two-pass process to emit the final binary [docs/6. Memory Mapping Specification.md, docs/7. Context Switching Specification.md].

#### Pass 1: Section Placement & Global Symbol Collection
The goal of this pass is to determine where every file’s code segment will live in the final memory map and to calculate the absolute address of every exported symbol.

1. **Initialize Address Counter**: Set `current_address = base_address`.
2. **Assign Layout**: Iterate through each object file sequentially:
    * Record `file_base_address[file] = current_address`.
    * Increment `current_address` by the size of the file's code/data block.
3. **Build the Global Symbol Table**: Create a master map of `Symbol Name -> Absolute Address`:
    * For every object file, iterate through its `EXPORT` symbols.
    * Calculate its absolute address: `absolute_address = file_base_address[file] + symbol.offset`.
    * Store this in the master table.
    * *Error Check*: If a symbol name is already in the master map, throw a **Duplicate Symbol Error**.

#### Pass 2: Relocation & Binary Assembly
The goal of this pass is to copy the code payloads into a single output buffer and modify (patch) the immediate values of instructions that depended on unresolved symbols.

1. **Allocate Output Buffer**: Create a contiguous buffer matching the final total size of all code segments.
2. **Copy Raw Payloads**: Copy each object file’s code block to its designated offset in the output buffer.
3. **Resolve Relocations**: For every object file, iterate through its **Relocation Table**:
    * Look up the relocation's `Symbol Name` in the master **Global Symbol Table**.
    * If the symbol is missing, throw an **Unresolved External Symbol Error**.
    * Calculate the **Instruction's Absolute Address**:
      `inst_addr = file_base_address[file] + relocation.offset`.
    * Read the unpatched 16-bit instruction from the output buffer at `inst_addr`.
    * Apply the patching algorithm based on `Relocation Type`:

        * **Type `ABS_16`** (e.g., `.fill symbol`):
            * Replace the target word with the symbol's absolute 16-bit address.

        * **Type `ABS_LUI`** (e.g., `lui reg, (symbol >> 6)`):
            * Extract the top 10 bits of the symbol's absolute address [docs/3. Creating 16-bit Constants, docs/4. Programing Specifications.md].
            * Mask these 10 bits into the instruction's immediate field (bits `9-0`) [docs/1. Hardware Specifications.md].

        * **Type `ABS_LLI`** (e.g., `addi reg, reg, (symbol & 0x3F)`):
            * Extract the bottom 6 bits of the symbol's absolute address [docs/3. Creating 16-bit Constants, docs/4. Programing Specifications.md].
            * Mask these 6 bits into the instruction's immediate field (bits `5-0`).

        * **Type `REL_7`** (e.g., `beq r1, r2, symbol`):
            * Calculate the PC-relative offset:
              `offset = symbol_absolute_address - (inst_addr + 1)` [docs/3. Assembler Specifications.md, docs/2. Instruction Specifications.md].
            * *Error Check*: Verify that `offset` fits within the signed 7-bit range (`-64` to `63`) [docs/1. Hardware Specifications.md]. If it does not, throw a **Branch Target Out of Range Error**.
            * Mask the 7-bit signed value into the instruction's immediate field (bits `6-0`) [docs/1. Hardware Specifications.md].

    * Write the patched instruction back to the output buffer at `inst_addr`.

4. **Emit Binary**: Write the finalized contiguous output buffer to disk.

---

### 3. Design Suggestions for Implementation

* **Local Labels vs. Global Labels**: Labels starting with a dot (e.g., `.loop`) can be treated as *file-local*. The assembler can resolve these relative to the file start during the assembly phase, meaning they do not need to be exported or passed to the linker. Only non-local labels (e.g., `math_multiply:`) should be added to the Object File's symbol table.
* **Common Entry Point**: You can specify that the linker must find a global symbol named `_start` or `main` and place a bootstrap instruction at the very beginning of your output binary (`base_address`) that jumps to that symbol's resolved absolute address, allowing the files to be linked in any order.
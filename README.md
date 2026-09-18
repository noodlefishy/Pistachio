# Pistachio & Ink Toolchain ✨

> A complete, educational 16-bit RISC computing ecosystem built from scratch in Kotlin 
> Designed to _demystify_ low-level systems, computer architecture, and toolchain design


Pistachio is a custom 16-bit RISC virtual machine inspired by Bruce Jacob's RiSC-16. It features a complete custom
assembly language (**Ink Assembly**), a multi-file two-pass **Linker**, an interactive GDB-style **Debugger (
`lx-dbg`)**, and a fancy **Standard Library**

---

## Architecture at a Glance

* **Word Size & Memory:** 16-bit native data paths; 64K words (word-addressed, NOT byte-addressed)
* **Register File:** 8 general-purpose registers (`r0`–`r7`) following a strict ABI:
    * `r0`: Hardwired to zero (`0x0000`)
    * `r1`–`r3`: Subroutine arguments (`r1` = return value)
    * `r4`–`r5`: Callee-saved scratchpads
    * `r6` (`SP`): Stack pointer
    * `r7` (`LR`): Subroutine return address link register
* **Minimal Hardware Core:** Exactly 8 instructions (`add`, `addi`, `nand`, `lui`, `lw`, `sw`, `beq`, `jalr`)
* **Memory-Mapped Peripherals (MMIO):**
    * **Console (`0xFF00`–`0xFF02`):** Terminal STDIN / STDOUT / status polling
    * **Display (`0xFF03`–`0xFF4E`):** 8×8 RGB565 graphical canvas rendered via Java Swing
    * **Packing Accelerator (`0xFF60`–`0xFF6C`):** 4-wide SIMD coprocessor for parallel math, SWAR, and dot products

---

## Repository Overview

```text
.
├── compiler/         # Lexer, Tokenizer, AST macro expanders, and Two-Pass Linker
├── hardware/         # CPU loop, ALU, Memory Bus, and MMIO Peripherals
├── terminal/         # lx CLI entrypoint, Smart Disassembler, and Debugger REPL
├── program files/    # Ink Assembly standard library (alloc, fmt, io, maths, mem, string)
├── examples/         # Demos (Recursion, SIMD, String formatting, Exploit POCs)
├── configurations/   # JSON profiles for CPU clock delays and debug dumps
└── lx                # Root driver CLI launcher
```

---

## Quick Start

### Prerequisites

- **JDK 17 or higher** installed
- A Unix like system

### Build & Run

The included `lx` executable wrapper manages building and running automatically:

```bash
# Compile, link, and execute with standard libraries
./lx -i "examples/2. Abstracted hello world.lx" "program files/lib/io.lx"

# Launch interactive GDB-like debugger
./lx -i main.lx "program files/lib" --debug
```

---

## CLI Usage (`lx`)

```bash
./lx <command> [files...] [options]
```

* `-i <files...>`: Compiles, links, and immediately executes source files in memory.
* `-b <files...> -o <out.bin>`: Compiles and links into a relocatable binary without running.
* `-r <file.bin>`: Loads and executes a pre-compiled binary.
* `-d <file.bin>`: Decodes a binary using the **Smart Disassembler** (reconstructs high-level macros).
* `-t <file.lx>`: Tokenizes and prints raw instruction AST representations.
* `-x <file.bin>`: Generates a static ASCII/Hex memory dump.

**Options:** `--debug` (interactive REPL), `--debugf` (silent debug dumps), `--dump` (post-mortem hex dump).

---

## Interactive Debugger (`lx-dbg`)

Append `--debug` to run interactively with execution traces and register inspection:

```text
(lx-dbg) > s            # Step single instruction (Enter auto-repeats)
(lx-dbg) > regs         # Print register states (decimal and hex)
(lx-dbg) > b <target>   # Set breakpoint at address or label
(lx-dbg) > c            # Continue execution
(lx-dbg) > x <addr> [N] # Memory hex dump
(lx-dbg) > xvon <addr>  # Disassemble live memory
```

---

## Ink Assembly Preview

Ink Assembly blends RISC simplicity with high-level macro ergonomics:

```assembly
main:
    movi r6, stack          // Initialize stack pointer
    call heap_init          // Start first-fit heap allocator

    // Allocate dynamic 2-word Node [data, next]
    movi r1, 2
    call alloc              // r1 = node pointer

    movi r2, 42
    sw   r2, [r1 + 0]       // node->data = 42
    sw   r0, [r1 + 1]       // node->next = NULL

    halt

stack:
    .space 100
```

---

## Documentation & Specifications

Detailed hardware specifications and tutorials are available on the
**[Pistachio Wiki](https://github.com/noodlefishy/Pistachio/wiki)**:

- **[Architecture Specifications](https://github.com/noodlefishy/Pistachio/wiki/Pistachio-Architecture-Specifications)** —
  Word sizes, registers, and instruction encoding.
- **[Core Instruction Set](https://github.com/noodlefishy/Pistachio/wiki/Core-Instruction-Set)** — Hardware opcodes
  and bit layouts.
- **[Calling Convention & ABI](https://github.com/noodlefishy/Pistachio/wiki/Calling-Convention-&-ABI)** — Subroutine
  linkage and register preservation rules.
- **[Pseudo-Instructions & Macros](https://github.com/noodlefishy/Pistachio/wiki/Pseudo-Instructions-&-Macros)** —
  Syntactic sugar and expansions (`call`, `movi`, `sub`, `blt`).
- **[LEAF Binary Format](https://github.com/noodlefishy/Pistachio/wiki/Loomy-Executable-Arbitrary-Format-(LEAF))** —
  Executable format and symbol tables.
- **[Memory Map & MMIO](https://github.com/noodlefishy/Pistachio/wiki/Memory-Mapping-Specification)** — Vector,
  kernel, userland, and device address maps.
- **[Standard Library API Reference](https://github.com/noodlefishy/Pistachio/wiki/Standard-Library-API-Reference)** —
  Heap (`alloc`), formatting (`printf`), string, and math routines.
- **[Interactive Debugger (
  `lx-dbg`) Reference](https://github.com/noodlefishy/Pistachio/wiki/Interactive-Debugger-(lx-dbg)-Reference)** —
  Debugger commands and workflows.

---

Made with 💛, Kotlin, and carrots
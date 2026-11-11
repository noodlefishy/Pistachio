# Pistachio & Ink Toolchain ✨

> A complete, educational 16-bit RISC computing ecosystem built from scratch in Kotlin
> Designed to _demystify_ low-level systems, computer architecture, and toolchain design


Pistachio is a custom 16-bit RISC virtual machine inspired by Bruce Jacob's RiSC-16. It features a complete custom
assembly language (**Ink Assembly**), a multi-file two-pass **Linker**, an interactive GDB-style **Debugger (
`lx-dbg`)**, and a fancy **Standard Library**

---

## Architecture at a Glance

- **Word Size & Memory:** Native 16-bit data paths, registers, and memory addresses; 64K words
  ($2^{16} = 65,536\text{ words}$, word-addressed).
- **Minimal Core Hardware:** Exactly **8 instructions**:
  ```text
    add   addi    nand    lui    lw    sw    beq    jalr
  ```
- **Register File:** 8 general-purpose registers (`r0`–`r7`) with a strict ABI:
    - `r0`: Hardwired to zero (`0x0000`).
    - `r1`–`r3`: Subroutine arguments (`r1` holds primary return value).
    - `r4`–`r5`: Callee-saved scratchpads.
    - `r6` (`SP`): Ascending stack pointer (`push` increments `r6`).
    - `r7` (`LR`): Subroutine link register.
- **Integrated Peripherals (MMIO `0xFE00`–`0xFFFF`):**
    - **Terminal Console (`0xFF00`–`0xFF02`):** Character streaming and input status polling.
    - **Pixastachio Display (`0xFF03`–`0xFF0C`):** 64×64 RGB565 retro canvas rendered via Java Swing and WebAssembly.
    - **Programmable Interval Timer (`0xFF10`–`0xFF13`):** Hardware millisecond countdowns.
    - **DMA Controller (`0xFF30`–`0xFF34`):** Memory-to-memory and memory-to-port block transfers.
    - **Packing Accelerator (`0xFF60`–`0xFF6C`):** 4-wide SIMD coprocessor for parallel math and 8-bit SWAR.
    - **Socket Controller (`0xFF70`–`0xFF78`):** Hardware network interface for UDP words and TCP byte streams.
    - **Block Storage Drive (`0xFF80`–`0xFF85`):** 256-word sector DMA disk controller.

---

## Repository Overview

```text
.
├── compiler/         # Lexer, Tokenizer, AST parser, and Two-Pass Linker
├── hardware/         # CPU loop, ALU, Memory Bus, and MMIO Peripherals
├── terminal/         # lx CLI entrypoint, Smart Disassembler, and Debugger REPL
├── web/              # WebAssembly (Wasm-JS) browser-based visual debugger
├── program files/    # Ink Assembly standard libraries (alloc, fmt, gfx, io, maths, mem, string)
├── examples/         # Demos (Conway's Life, P2P Sockets, Recursion, Exploit POCs)
├── tools/            # Browser-based Sprite Editor and 3x5 Font Generator
└── lx                # All-in-one root CLI driver launcher
```

---

## Quick Start

### Prerequisites

- **JDK 17 or higher** installed
- A Unix like system (macOS or Linux)

### Build & Run

The included `lx` executable wrapper manages building and running automatically:

```bash
git clone --depth 1 https://github.com/noodlefishy/Pistachio && cd Pistachio

# Install command-line driver symlink
./gradlew installDist



# Compile, link, and execute with standard libraries
./lx -i "examples/2. Abstracted hello world.lx" "program files/lib/io.lx"

# Launch Conway's Game of Life with full graphical UI
./lx -i "examples/14. Conways game of life.lx" "program files/lib"

# Launch the interactive GDB-style debugger
./lx -i "examples/8. Dynamically Linked List.lx" "program files/lib" --debug
```

---

## CLI Usage (`lx`)

```bash
./lx <command> [files...] [options]
```

- `-i <files...>`: Compiles, links, and executes source files in memory.
- `-b <files...> -o <out.bin>`: Compiles and links into a binary file.
- `-r <file.bin>`: Loads and runs a pre-compiled binary.
- `-d <file.bin>`: Decodes a binary using the **Smart Disassembler** (`xvon`).
- `-t <file.lx>`: Tokenises and prints raw instruction AST representations.
- `-x <file.bin>`: Generates a static ASCII/Hex memory dump.

**Execution Options:**

- `--debug`: Attaches the interactive REPL before execution begins.
- `--debugf`: Silently generates workspace debug artefacts (`.map`, `.disasm`, `.hex`, `.regs`).
- `--profile`: Runs the CPU execution profiler, printing kIPS and visual workload bars.
- `--dump`: Emits a complete memory hex dump upon program halt or crash.

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

## Ink Assembly Language

Ink Assembly balances bare-metal simplicity with clean pretty boy energy:

- **No commas:** Operands are separated strictly by single spaces.
- **Scoped labels:** Local jump targets start with a dot (`.loop`) and are indented with tabs.
- **Non-destructive macros:** High-level abstractions (`movi`, `call`, `ret`, `push`, `pop`, `sub`, `blt`, `bgt`) expand
  automatically without clobbering unmentioned registers.

```assembly
main:
	movi r6 [stack]
	call [heap_init]

	// Allocate a dynamic 2 word Node [data, next]
	movi r1 #2
	call [alloc]
	mov r4 r1

	movi r2 #42
	sw r2 [r4 + #0] // node->data = 42
	sw r0 [r4 + #1] // node->next = NULL

	halt

stack:
    .space 100
```

---

## What's Working Today

- [x] **Full 8 instruction Cpu Core** with microsecond-accurate instruction and bus timings.
- [x] **Lexer & Multi-Pass Ast Parser** with automatic iterative branch relaxationification.
- [x] **Two-Pass Relocating Linker** with symbol imports, exports, and relocation patching.
- [x] **Smart Disassembler (`xvon`)** that reconstructs high-level macros from raw machine words.
- [x] **Interactive Debugger (`lx-dbg`)** with breakpoints, single-stepping, and register deltas.
- [x] **Execution Profiler** tracking execution cycles, hotspot subroutines, and kIPS metrics.
- [x] **Comprehensive Standard Library:** First-fit dynamic heap allocator (`alloc.lx`), 16-cycle binary long division
  (`maths.lx`), 2D graphics engine (`gfx.lx`), string manipulation (`string.lx`), and formatted `printf` (`fmt.lx`).
- [x] **Hardware Peripherals:** Swing & Canvas display, timer, DMA controller, and TCP/UDP sockets.

---

## What’s Working Today

- [x] **Full 8-instruction CPU Core** with microsecond-accurate instruction and bus timings.
- [x] **Lexer & Multi-Pass AST Parser** with automatic iterative branch relaxation.
- [x] **Two-Pass Relocating Linker** with symbol imports, exports, and relocation patching.
- [x] **Smart Disassembler (`xvon`)** that reconstructs high-level macros from raw machine words.
- [x] **Interactive Debugger (`lx-dbg`)** with breakpoints, single-stepping, and register deltas.
- [x] **Execution Profiler** tracking execution cycles, hotspot subroutines, and kIPS metrics.
- [x] **Comprehensive Standard Library:** First-fit dynamic heap allocator (`alloc.lx`), 16-cycle binary long division
  (`maths.lx`), 2D graphics engine (`gfx.lx`), string manipulation (`string.lx`), and formatted `printf` (`fmt.lx`).
- [x] **Hardware Peripherals:** Swing & Canvas display, timer, DMA controller, and TCP/UDP sockets.

---

## Plans: Pistachio-μK (Operating System)

With the processor, toolchain, and standard libraries fully working, the current focus is architecting
**Pistachio-μK** a minimalist, synchronous L4-style microkernel designed to bring preemptive multitasking, memory
isolation, and a command shell to the platform

- **Base & Limit Memory Isolation:** Zero-origin logical addressing protecting private task partitions.
- **Preemptive Round-Robin Scheduling:** 100 Hz timer tick preemption with 16-word Process Control Blocks.
- **Synchronous Direct Register IPC:** Zero-copy message passing transferred directly within CPU registers.
- **Filesystem-as-a-Service (`fs_srv`):** An isolated userland server managing on-disk Inodes and DMA sectors.
- **Interactive Shell (`psh`):** A command interpreter supporting built-in tools, process spawning, and stream
  redirection.

Track upcoming operating system implementation milestones in **[`Roadmap.md`](Roadmap.md)**!

## Documentation & Specifications

Detailed hardware specifications and tutorials are available on the
**[Pistachio Wiki](https://github.com/noodlefishy/Pistachio/wiki)**:

-
**[Architecture Specifications](https://github.com/noodlefishy/Pistachio/wiki/Pistachio-Architecture-Specifications)** —
Word sizes, registers, and instruction encoding.
- **[Core Instruction Set](https://github.com/noodlefishy/Pistachio/wiki/Core-Instruction-Set)** — Hardware opcodes and
  bit layouts.
- **[Calling Convention & ABI](https://github.com/noodlefishy/Pistachio/wiki/Calling-Convention-&-ABI)** — Subroutine
  linkage and register preservation rules.
- **[Pseudo-Instructions & Macros](https://github.com/noodlefishy/Pistachio/wiki/Pseudo-Instructions-&-Macros)** —
  Syntactic sugar and expansions (`call`, `movi`, `sub`, `blt`).
- **[LEAF Binary Format](https://github.com/noodlefishy/Pistachio/wiki/Loomy-Executable-Arbitrary-Format-(LEAF))** —
  Executable format and symbol tables.
- **[Memory Map & MMIO](https://github.com/noodlefishy/Pistachio/wiki/Memory-Mapping-Specification)** — Vector, kernel,
  userland, and device address maps.
- **[Standard Library API Reference](https://github.com/noodlefishy/Pistachio/wiki/Standard-Library-API-Reference)** —
  Heap (`alloc`), formatting (`printf`), string, and math routines.
- **[Interactive Debugger (
  `lx-dbg`) Reference](https://github.com/noodlefishy/Pistachio/wiki/Interactive-Debugger-(lx-dbg)-Reference)** —
  Debugger commands and workflows.

---

Made with 💛, Kotlin, and carrots
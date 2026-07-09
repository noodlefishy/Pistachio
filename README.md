# Pistachio & Ink Toolchain

This is a full-stack, educational 16-bit system built entirely in Kotlin.  
This repository contains a custom 16-bit RISC CPU (the **Pistachio VM**) based on Bruce Jacob's RiSC16,
an assembler and linker for a custom language (**Ink Assembly**),
and a micro-kernel designed to handle traps, contexts, and hardware MMIO.

Keep in mind this is an educational project to understand lower level systems !!

---

## Repository Structure

The codebase is organised as a Gradle multi-module project:

```text
├── compiler/                  // Ink Assembler & the two-pass Linker. 
│   ├── src/.../Parser.kt      // Translates Ink Assembly to intermediate instructions & objects. 
│   └── src/.../Linker.kt      // Combines relocatable object files into a LEAF binary. 
├── hardware/                  // The Pistachio Virtual Machine. 
│   ├── src/components/...     // CPU clock, register, ALU, Memory, and execution tick loop. 
│   └── src/.../Backend.kt     // Instruction encoder and decoder (16-bit word representation). 
├── terminal/                  // CLI interface and compiler controller. 
│   └── src/.../Main.kt        // Command router handling compilation, linking, & VM execution. 
├── configurations/...         // JSON files to adjust emulator speeds and debug modes. 
├── program files/             // Userland Ink Assembly programs (.lx). 
│   ├── main.lx.               // App entrypoint (unit testing). 
│   └── lib/...                // Linkable LEAF binaries. 
└── kernel.lx.                 // The OS Kernel (manages bootstrapping, context switching, and vectors).    
```

---

## Ink Assembly Preview

Here is a look at **Ink Assembly** implementing a recursive-style stack-based comparison:

```assembly
// maths.lx
lessThan:
    push r2
    push r3

    nand r2, r2, r2     // Negate R2 (1's complement)
    movi r3, 1
    add r2, r2, r3      // R2 = -R2 (2's complement negation)

    add r1, r1, r2      // R1 = R1 - R2

    movi r3, $SBIT      // Isolate sign bit (MSB)
    nand r1, r1, r3
    nand r1, r1, r1

    pop r3
    pop r2
    ret
```

---

## Quick Start

Build the entire toolchain and create a local executable command line tool in your root folder:

### 1. Build the Repository

```bash
# macOS / Linux:
./gradlew installDist

# Windows:
gradlew.bat installDist
```

### 2. Run your first file

This command builds, links, and runs a userland application with the OS kernel loaded into Pistachio
memory:

```bash
# PS: This might not work every commit so try and make your own !!
./lx -os kernel.lx program_files/main.lx
```

---

## Documentation

For complete reference guides, calling conventions, hardware specs, and software algorithm tutorials, visit the **(GitHub
Wiki)[https://github.com/noodlefishy/Pistachio/wiki]**. 

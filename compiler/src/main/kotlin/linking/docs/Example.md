Here is a complete end-to-end example of writing two source files, assembling them into relocatable object files (`.o` JSON format), and processing them through the two-pass linking algorithm to generate the final binary payload.

---

### 1. The Source Code Files (`.kar`)

**File 1: `math.kar`**
This file defines a simple addition function and exports the label `math_add`.

```assembly
# math.kar
math_add:
    add r1, r1, r2  # R1 = R1 + R2
    jalr r0, r7, 0  # Return (Assumes the caller saved the return address in R7)
```

**File 2: `main.kar`**
This file initializes the stack, prepares arguments, calls the external `math_add` function, and halts.

```assembly
# main.kar
main:
    movi r6, 0xFE00   # Set stack pointer
    movi r1, 5        # Argument 1
    movi r2, 10       # Argument 2
    
    # Call external math_add function
    movi r5, math_add # Load address of math_add into R5 (Requires Relocation!)
    jalr r7, r5, 0    # Jump to R5, save return address to R7
    
    halt              # jalr r0, r0, 1
```

---

### 2. The Assembled Object Files (`.o` JSON)

The Assembler reads the `.kar` files independently (starting each at an imaginary address of `0x0000`). When it encounters the `movi r5, math_add` macro in `main.kar`, it emits placeholder zeroes for the immediate values and logs two relocation entries.

**File 1: `math.o`**
```json
{
  "header": {
    "fileName": "math.kar",
    "codeSize": 2,
    "symbolCount": 1,
    "relocationCount": 0
  },
  "payload": [
    "0x0482",  // add r1, r1, r2
    "0xE380"   // jalr r0, r7, 0
  ],
  "symbolTable": [
    {
      "name": "math_add",
      "type": "EXPORT",
      "offset": 0
    }
  ],
  "relocationTable": []
}
```

**File 2: `main.o`**
*(Note: `movi` expands to two instructions: `lui` and `addi`. Thus, `movi r5, math_add` requires two separate relocations at offsets `6` and `7`)*.

```json
{
  "header": {
    "fileName": "main.kar",
    "codeSize": 10,
    "symbolCount": 2,
    "relocationCount": 2
  },
  "payload": [
    "0x7BF8",  // 0: lui r6, 0x3F8  (movi r6, 0xFE00)
    "0x3B00",  // 1: addi r6, r6, 0 
    "0x6800",  // 2: lui r1, 0      (movi r1, 5)
    "0x2905",  // 3: addi r1, r1, 5
    "0x7000",  // 4: lui r2, 0      (movi r2, 10)
    "0x320A",  // 5: addi r2, r2, 10
    "0x7400",  // 6: lui r5, 0      <-- PLACEHOLDER (movi r5, math_add)
    "0x3680",  // 7: addi r5, r5, 0 <-- PLACEHOLDER 
    "0xFE80",  // 8: jalr r7, r5, 0
    "0xE001"   // 9: halt           (jalr r0, r0, 1)
  ],
  "symbolTable": [
    {
      "name": "main",
      "type": "EXPORT",
      "offset": 0
    },
    {
      "name": "math_add",
      "type": "IMPORT",
      "offset": 0
    }
  ],
  "relocationTable": [
    {
      "instructionOffset": 6,
      "symbolName": "math_add",
      "type": "ABS_LUI"
    },
    {
      "instructionOffset": 7,
      "symbolName": "math_add",
      "type": "ABS_LLI"
    }
  ]
}
```

---

### 3. The Linker Execution

The user runs: `lc -o out.bin main.o math.o` (assuming a target Base Address of `0x3000` for the Userland Segment).

#### Pass 1: Section Placement & Symbol Harvesting
The linker assigns absolute addresses to every file block and harvests `EXPORT` symbols into a Master Symbol Map:

1. **`main.o`**:
    * Assigned Base Address: `0x3000`
    * Exported `main` -> `0x3000 + offset 0` = **`0x3000`**
    * Next Available Address = `0x3000 + 10 words` = `0x300A`

2. **`math.o`**:
    * Assigned Base Address: `0x300A`
    * Exported `math_add` -> `0x300A + offset 0` = **`0x300A`**

**Master Symbol Table Output:**
* `main` = `0x3000`
* `math_add` = `0x300A`

---

#### Pass 2: Relocation & Patching
The linker allocates a 12-word contiguous array for the final executable and copies the payloads in. It then checks the relocation tables.

`main.o` has two relocations at its internal offsets `6` and `7` that depend on `math_add` (`0x300A`).

1. **Calculating the `math_add` address components:**
    * Absolute Target: `0x300A` (`12298` in decimal, `0011 0000 0000 1010` in binary).
    * `LUI` Top 10 bits (`0x300A >> 6`): `0x0C0` (`00 1100 0000`).
    * `LLI` Bottom 6 bits (`0x300A & 0x3F`): `0x0A` (`00 1010`).

2. **Patching `ABS_LUI` at offset 6 (`0x7400`):**
    * Instruction is `lui r5, 0` (`0111 0100 0000 0000`).
    * Linker OR's the top 10 bits (`0x0C0`) into the instruction.
    * `0x7400 | 0x0C0` = **`0x74C0`**

3. **Patching `ABS_LLI` at offset 7 (`0x3680`):**
    * Instruction is `addi r5, r5, 0` (`0011 0110 1000 0000`).
    * Linker OR's the bottom 6 bits (`0x0A`) into the instruction.
    * `0x3680 | 0x0A` = **`0x368A`**

---

### 4. Final Output Binary

The patched, linked executable is finalized and written to disk. Notice how instructions 6 and 7 in `main.kar` now successfully contain the correct immediate values to load `0x300A` (the address of `math_add`) into `R5`.

```text
[
  0x7BF8,  // 0x3000: lui r6, 0x3F8
  0x3B00,  // 0x3001: addi r6, r6, 0 
  0x6800,  // 0x3002: lui r1, 0
  0x2905,  // 0x3003: addi r1, r1, 5
  0x7000,  // 0x3004: lui r2, 0
  0x320A,  // 0x3005: addi r2, r2, 10

  0x74C0,  // 0x3006: lui r5, 0x0C0   <-- PATCHED by Linker!
  0x368A,  // 0x3007: addi r5, r5, 10 <-- PATCHED by Linker!

  0xFE80,  // 0x3008: jalr r7, r5, 0
  0xE001,  // 0x3009: halt

  0x0482,  // 0x300A: add r1, r1, r2  (math_add segment starts here)
  0xE380   // 0x300B: jalr r0, r7, 0
]
```
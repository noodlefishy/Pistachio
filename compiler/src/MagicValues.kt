package io.cuttlefish

enum class MagicValues(val value: Short, val hex: String) {
    /**
     * `All Ones`
     *
     * Used for bitwise NOT operations (NAND with AONS) and represents -1.
     */
    AONS(-1, "0xFFFF"),

    /**
     * `Sign Bit`
     *
     * The 16th bit (MSB). Used to check if a 16-bit word is negative.
     */
    SBIT(-32_768, "0x8000"),

    /**
     * `Maximum Signed Integer`
     *
     * The largest possible signed 16-bit value (32,767).
     */
    MXIT(32_767, "0x7FFF"),

    /**
     * `Unit Value`
     *
     * The value 1. Useful for increments and Two's Complement negation.
     */
    UNIT(1, "0x0001"),

    /**
     * `Opcode Mask`
     *
     * Isolates bits 15-13 to identify the instruction type.
     */
    M_OC(-8192, "0xE000"),

    /**
     * `Register A Mask`
     *
     * Isolates bits 12-10 (the destination register for most ops).
     */
    M_RA(7168, "0x1C00"),

    /**
     * `Register B Mask`
     *
     * Isolates bits 9-7 (the first source register).
     */
    M_RB(896, "0x0380"),

    /**
     * `Register C Mask`
     *
     * Isolates bits 2-0 (the second source register in RRR-type).
     */
    M_RC(7, "0x0007"),

    /**
     * `Immediate 7-bit Mask`
     *
     * Isolates the 7-bit signed immediate field (bits 6-0) used in ADDI, LW, SW, BEQ.
     */
    M_I7(127, "0x007F"),

    /**
     * `Immediate 10-bit Mask`
     *
     * Isolates the 10-bit immediate field (bits 9-0) used in the LUI instruction.
     */
    M_I10(1023, "0x03FF"),

    /**
     * `Opcode Shift`
     *
     * Number of bits to shift right to get the Opcode value (0-7).
     */
    S_OC(13, "0x000D"),

    /**
     * `Register A Shift`
     *
     * Number of bits to shift right to get the Register A index (0-7).
     */
    S_RA(10, "0x000A"),

    /**
     * `Register B Shift`
     *
     * Number of bits to shift right to get the Register B index (0-7).
     */
    S_RB(7, "0x0007"),

    /**
     * `Highest Positive Lower Immediate`
     *
     * The largest 7-bit signed value (63) allowed in ADDI/LW/SW/BEQ.
     */
    HPLI(63, "0x003F"),

    /**
     * `Lowest Negative Lower Immediate`
     *
     * The most negative 7-bit signed value (-64) allowed in ADDI/LW/SW/BEQ.
     */
    LNLI(-64, "0xFFC0"),

    /**
     * `Highest Upper Immediate`
     *
     * The largest 10-bit value (1023) allowed in an LUI instruction.
     */
    HUI(1023, "0x03FF"),

    /**
     * `Lower Byte Mask`
     *
     * Mask to isolate the bottom 8 bits (useful for ASCII/Byte operations).
     */
    M_LB(255, "0x00FF"),

    /**
     * `Upper Byte Mask`
     *
     * Mask to isolate the top 8 bits.
     */
    M_UB(-256, "0xFF00");
}

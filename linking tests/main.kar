main:
    movi r1, 5
    movi r2, 10
    
    movi r5, math_add // Load address of math_add into R5 (Requires Relocation!)
    jalr r7, r5, 0    // Jump to R5, save return address to R7
    
    halt

useless:
    nop
#include <stdio.h>
#include <stdint.h> // Smart cookie

uint16_t lessThan(uint16_t r1, uint16_t r2) {
    uint16_t r3;
    r2 = ~r2;
    r2 = (uint16_t)(r2 + 1);
    r1 = (uint16_t)(r1 + r2);
    r3 = (uint16_t)0x8000;
    r1 = ~(r1 & r3);
    r1 = ~r1;
    return r1;
}

int main() {
    uint16_t a = 5;// r1
    uint16_t b = 10; // r2

    uint16_t result = lessThan(a, b);

    if (result == 0x8000) {
        printf("%d is less than %d (Result register r1: 0x%04X)\n", a, b, (uint16_t)result);
    } else {
        printf("%d is NOT less than %d (Result register r1: 0x%04X)\n", a, b, (uint16_t)result);
    }

    return 0;
}
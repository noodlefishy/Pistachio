#include <stdio.h>
#include <stdint.h>

uint16_t r1; uint16_t r2; uint16_t r3; uint16_t r4; uint16_t r5; uint16_t r6; uint16_t r7;

uint16_t stack[1024];

void push(uint16_t value) {
	stack[r6] = value;
	r6++;
}

uint16_t pop() {
	r6--;
	return stack[r6];
}


void lessThan() { // if r1 is less than r2(r1 < r2)  , r1 = 0x8000 else r1 = 0
	push(r3);
    r2 = ~r2;
    r2 = (uint16_t)(r2 + 1);
    r1 = (uint16_t)(r1 + r2);
    r3 = (uint16_t)0x8000;
    r1 = ~(r1 & r3);
    r1 = ~r1;
    r3 = pop();
}

/*
if (n <= 1){
        return n;
    }
    return nthFibonacci(n - 1) + nthFibonacci(n - 2);
*/
void fib() {
	r2 = 1
	lessThan()
	if (r1 == 0) {
		return
	}
	if
}


int main() {
	r6 = 0;

	r1 = 5;
	r2 = 1;
	lessThan();
	printf("%d",r1);

}
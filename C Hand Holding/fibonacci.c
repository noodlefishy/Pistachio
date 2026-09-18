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
  	push(r4);
    push(r5);

	r4 = r1;

	r1 = r4;
	r2 = 2;
	lessThan();
	if (r1 != 0) {
		r1 = r4;
		goto complete;
	}
	r1 = (uint16_t)(r4 - 1);
	fib();
	r5 = r1;

    r1 = (uint16_t)(r4 - 2);
    fib();

    r1 = (uint16_t)(r5 + r1); // r1 = fib(n - 1) + fib(n - 2)

	complete:
		r5 = pop();
		r4 = pop();

}


int main() {
	r6 = 0;

    r1 = 10;
    fib();
	printf("%d",r1);

}
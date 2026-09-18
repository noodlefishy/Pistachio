#include <stdio.h>

int nthFibonacci(int n) {

    if (n <= 1){
        return n;
    }
    return nthFibonacci(n - 1) + nthFibonacci(n - 2);
}


int main(){
    int n = 10;
    int result = nthFibonacci(n);
    printf("%d", result);
    return 0;
}
#include <stdio.h>
#include <stdlib.h>


typedef struct {
	int data;
	struct Node* next;
} Node ;

void add(Node** initReference, int dataNew) {
	Node* new = (Node*)malloc(sizeof(Node))
	new->data = dataNew
	new->next = *initReference
}


int main() {
	Node* initial = (Node*)malloc(sizeof(Node));
    Node* second = (Node*)malloc(sizeof(Node));
    Node* third = (Node*)malloc(sizeof(Node));



}
#include <stdio.h>
#include <stdlib.h>

typedef struct Node {
    int data;
    struct Node* next;
} Node;

Node* createNode(int data) {
    Node* newNode = (Node*)malloc(sizeof(Node));

    newNode->data = data;
    newNode->next = NULL;
    return newNode;
}

// O(1) time
void insertAtFront(Node** headRef, int data) {
    Node* newNode = createNode(data);
    newNode->next = *headRef;
    *headRef = newNode;
}

void printList(Node* head) {
    Node* current = head;
    while (current != NULL) {
        printf("%d -> ", current->data);
        current = current->next;
    }
    printf("NULL\n");
}

void freeList(Node* head) {
    Node* tmp;
    while (head != NULL) {
        tmp = head;
        head = head->next;
        free(tmp);
    }
}

int main() {
    Node* head = NULL;

    insertAtFront(&head, 30);
    insertAtFront(&head, 20);
    insertAtFront(&head, 10);

    printf("Linked List Elements: ");
    printList(head); // Output: 10 -> 20 -> 30 -> NULL

    freeList(head);
    head = NULL;

    return 0;
}

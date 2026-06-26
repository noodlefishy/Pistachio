


- 0xFF00 = Output port (write)
  - Unit -> print char
- 0xFF01 = Input port (read)
  - Short -> (short) input.read 
- 0xFF02 = Buffer available Port (read)
  - Short -> if input.bufferAvailable > 0 { 1 } else { 0 }
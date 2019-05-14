# Summary

This document is about an early - probably the first - attempt to port the [official IOTA Ict node](https://github.com/iotaledger/ict.git) implementation written in Java to the Rust programming language. This rather basic implementation was named **Ictarus** and we will be using this term for simple reference. Its source code can be found [here](https://github.com/Alex6323/Ictarus.git). The author's intention was to get a good understanding about the inner workings of the Ict node software and at the same time deepen his knowledge about the Rust programming language. This document is intended to summarize the findings and explain some of the architectural deviations from the Java prototype that were necessary to end up with an idiomatically written Rust software.

# About using Rust

According to Wikipedia [Rust](https://en.wikipedia.org/wiki/Rust_(programming_language)) is a multi-paradigm systems programming language focused on safety, especially safe concurrency, which is syntactically similar to C++, but is designed to provide better memory safety while maintaining high performance. 

Some of Rust's most **notable** features are:
* compiled language
* no automated garbage collection
* statically typed (but uses type inference to reduce boilerplate)
* forced error handling
* immutable variables by default
* follows composition over inheritance
* functional programming patterns (iterators, generators, closures)
* built-in support for tests and benchmarks
* easy-to-use tools for toolchain and dependency management for improved productivity

What makes Rust **unique** though is its ability to be not only a very safe language (guaranteed memory safety, no data races) but also be on par with the most performant languages in existence today that traditionally would have been picked instead. But compared to those with Rust one no longer has to sacrifice safety over performance or vice versa. 

How does Rust achieve that? First and foremost by restricting itself to only employ *zero-cost abstractions*. In simple terms that basically means, that
1. You do not "pay" for abstractions that you don't use.
2. If you use a certain abstraction you cannot improve performance by hand-coding it.

 On the other hand Rust introduces some **new concepts**, most notably those of *Ownership and Borrowing* which are an evolutionary step forward in language design. Those concepts tackle the problem of secure memory and resource management, and applied make whole classes of bugs in Rust just impossible which plague software development since decades. It does so without impact on performance unlike other "solutions" to that problem like garbage collection.

The consequence of those concepts is however, that more care has to be taken upfront when laying out the architecture. When porting software written in an OOP style to Rust one quickly realizes that some solutions cannot be reimplemented 1:1 with just changed syntax. The Rust compiler will often times reject those attempts and for good reasons. As an example: It is not as trivial as in other languages to create self-referential objects like nodes in a linked list or vertices in a graph. There is a whole (open) [book](https://rust-unofficial.github.io/too-many-lists/index.html) written to address this topic in Rust. Finding a good implementation here will be necessary to create a performant Tangle implementation that is capable of running even on low-end devices. All in all one can expect the final Rust Ict node to be looking very different internally compared to its Java precursor.

In the future the Ict network will be used for moving not only data but also large amounts (in sum over time) of value by utilizing directly connected and usually low-powered embedded IoT devices. The chosen programming language to build such a system should therefore be as safe *and* as performant as possible. Rust fulfills such requirements, and therefore should be a very good fit. But it should also be noted, that Rust is still a very young language and many libraries are still in heavy development themselves. There might be problems ahead related to some dependencies being in beta state themselves, or the Rust community still being relatively small. But due to the ever increasing popularity of Rust this can be expected to become less and less of a problem in the years to come.  

# About Ict

The **I**ota **c**ontrolled agen**t** (Ict) is a very flexible, modularized IOTA node. It can be very light-weight, but is not restricted to be so. Depending on the usecase it can be a very powerful node as well. It achieves this by implementing the IOTA eXtension Interface (IXI). The Ict node is specifically designed for the Internet of Things (IoT) and its diverse use cases. Targeted platforms are at first single-board computers (SBCs) like Raspberry Pis and similar devices. The final vision also includes a combination of microcontrollers (MCUs) with capabilities like the [Cortex-M4](https://developer.arm.com/ip-products/processors/cortex-m/cortex-m4) and upwards, and [tiny FPGAs](https://hackaday.io/project/26848-tinyfpga-b-series) to do the heavy lifting. To achieve this the node software is logically and programmatically separated into one single core component (Ict core) and in many so-called IXI modules, which can be written in different programming languages. The core itself might consist of replaceable units that allow for customized compilations to reduce the size of the binary if necessary.

While the Ict core implements the IOTA gossip protocol and is responsible for establishing a P2P network, buffering a certain amount of gossip, holding and updating the Tangle, and functioning as a gateway between the P2P network and its attached modules, the modules themselves define the actual functionality and the behavior of the node. For example, some module might turn the node into an access point for a P2P chat application, another module might turn the node into a data filtering permanode that diverts data into a database. The more resources available on the target platform the node is running on, the more modules can be hosted by the core and the more powerful the node becomes overall. On very constrained devices however it will be possible to create a very specific single-use behavior.

# About Ictarus

`Ictarus` is a Rust port of the Java Ict node software implementation. It was developed as a proof-of-concept (PoC) to show the feasibility of developing the Ict node software in Rust and not as a finished replacement for the Java version. Instead of adding features quickly and achieve feature parity the focus was on finding possible optimizations. Examples of that approach are bringing BCT-Curl and Troika to Rust, which can be found [here](https://github.com/Alex6323/bct_curl_rust), evaluating the use of transaction compression, which can be found [here](https://github.com/Alex6323/iota-lz4-udp) and helping the Java team with showcasing the use of protocol buffers in bridge.ixi which can be found [here](https://github.com/Alex6323/bridge.ixi-rust). There will be more such smaller "practical research" projects related to the network layer and the in-memory storage of the Tangle. Then we'll try to find consensus in our working group how to move forward.

# Differences between `Ictarus` and the Java reference implementation

On a high level `Ictarus` follows the Java reference implementation very closely, though not 100%. In fact both implementations cannot run on the same network at the moment. Why that is will be explained in the following paragraphs. Due to the Rust language specifics the internal realization differs in many ways. That is because Rust is more of a functional programming language than an object-oriented one. That has many consequences on the overall architecture. For example, there are no classes and no object inheritence in Rust. Rust uses containment hierarchies instead. 

## Using poll-based futures for concurrency

For concurrency in Rust it is idiomatic to use `futures` and `tokio` crates/libraries. Those are based on cooperative multitasking as opposed to preemptive multitasking, which simply means, that tasks try to make some progress for example on some I/O resource, and voluntarily yield execution back to the task executor if they can't. While this has the danger of locking up the whole application when implemented not carefully, it has the benefit of being a zero-cost abstraction resulting in maximum performance. Since being able to support weak devices is a major goal of this project this is of utmost importance.

## Attach request hashes only if required to spare bandwidth

The reason why the Rust based Ict implementation is not compatible with the Java version is that transactions only carry a request hash if an actual request needs to be send. In the Java implementation the packet size is always the same. It fills this space with 9s to indicate that there is no request. Peeking into the future it is very likely that some form of compression will be used which results in a dynamic packet size anyway. Another problem with using transactions as request carrier is that sometimes packets get dropped by ISPs due to their size. And another problem is, that requests cannot be sent idependently from the gossip which introduces unnecessary latencies. We are looking into protocols that support stream multiplexing to being able to send gossip and requests in parallel, which also reduces individual packet size.

## Sender is not implemented as a gossip listener

The Java version makes the sender a gossip listener, which could have been done in Rust as well. However, we tried a different approach, i.e. facilitating a shared sending queue across all threads. Upon receiving a transaction via gossip the `receiver` decides whether the transaction needs to be forwarded to the peers. If so, then it determines a random forward delay and adds the transaction hash to a priority-based sending queue which orders its elements depending on timestamps. The `sender` on the other hand also has access to that queue and pops the next transaction hash when the delay has been exceeded. If so the `sender` will pull the bytes from the local buffer, and then send the assembled packet.

## Disallow multiple requests for the same transaction from the same peer

During implementation the author decided to disallow requesting the same transaction over and over, because he reasoned this would encourage lazy neighbors who simply rely on the storage capacity of their peers. This will be further discussed among the working group whether adopt this mechanism, remove it, or find something in between.

## Missing features

Missing features are: 

* IXI interface
* Bundle creation
* Proof-of-work
* Tangle pruning
* Webinterface
* More...

# Architecture

Apart from minor differences `Ictarus` follows very closely the [architecture](https://raw.githubusercontent.com/iotaledger/ict/master/docs/assets/deployment.png) of the Java implementation.

## Project structure

The following table should give a basic overview about where things are located in `Ictarus`. Additionally the dependencies of each module are provided in a separate column, and types that hold most of the business logic of the node are highlighted:

| File              | Submodule | Function                                          | Dependencies                                               |
| ----------------- | --------- | ------------------------------------------------- | ---------------------------------------------------------- |
| `config.rs`       | root      | type for handling node configuration              | libstd, log                                                |
| `constants.rs`    | root      | central location of project wide constants        | lazy_static, regex                                         |
| `main.rs`         | root      | program entry point                               | libstd, pretty_env_logger                                  |
| `ixi.rs`          | root      | definition of the IOTA extension interface        | libstd                                                     |
| **`ictarus.rs`**  | root      | the actual node                                   | libstd, futures, log, priority_queue, stream_cancel, tokio |
| `listener.rs`     | network   | trait/interface definition of a gossip listener   | libstd                                                     |
| `neighbor.rs`     | network   | representation of a peer                          | libstd                                                     |
| **`receiver.rs`** | network   | handling of incoming transactions                 | libstd, futures, log, rand, tokio                          |
| **`sender.rs`**   | network   | handling of outgoing transactions                 | libstd, futures, log, tokio                                |
| **`tangle.rs`**   | model     | the Tangle and Vertex datastructure               | libstd, lazy_static                                        |
| `transaction.rs`  | model     | the datastructure for an IOTA transaction         | libstd                                                     |
| `time.rs`         | util      | utily functions and macros to handle time         | libstd                                                     |
| `curl.rs`         | crypto    | implementation of the Curl hashfunction           | *none*                                                     |
| `ascii.rs`        | convert   | converting other representations to ascii         | libstd                                                     |
| `bytes.rs`        | convert   | converting other representations to bytes         | *none*                                                     |
| `number.rs`       | convert   | converting other representations to numbers       | *none*                                                     |
| `trits.rs`        | convert   | converting other representations to trits         | *none*                                                     |
| `tryte_string.rs` | convert   | converting other representations to tryte strings | libstd                                                     |
| `trytes.rs`       | convert   | converting other representations to trytes        | *none*                                                     |
| `luts.rs`         | convert   | contains lookup tables for faster conversions     | libstd, lazy_static                                        |

The following paragraphs we will go into more detail how things were actually implemented roughly ordered by importance:

## Basic implementation of `Ictarus`

One of the most important abstractions is of couse the `Ictarus` node itself. Rather than explaining each single field we will let the code speak for itself, which should be easy to understand even if not familiar with Rust:

```Rust
pub type SharedSendingQueue = Arc<Mutex<PriorityQueue<(SharedKey81, SenderMode), Reverse<Instant>>>>;
pub type SharedRequestQueue = Arc<RwLock<VecDeque<Bytes54>>>;

pub struct Ictarus {
    config: SharedConfig,
    runtime: Runtime,
    state: State,
    tangle: SharedTangle,
    neighbors: SharedNeighbors,
    listeners: SharedListeners,
    sending_queue: SharedSendingQueue,
    request_queue: SharedRequestQueue,
    kill_switch: (Trigger, Tripwire),
}

pub enum State {
    Initializing,
    Running,
    Terminating,
    Off,
}

```
It is important to note, that types that are prefixed with `Shared` are types that are potentially shared across threads. The `Runtime` type is responsible for running the event loop and running the asynchronous tasks.

The API for this node looks like this:

```Rust
// Create a new Ictarus node from a config.
pub fn new(config: SharedConfig) -> Self

// Start the node.
pub fn run(&mut self) -> Result<(), Box<std::error::Error>>

// Block the main thread until kill signal.
pub fn wait_for_kill_signal(self)

// Stop the node immediatedly.
pub fn kill(mut self)

// Submit a message to the network.
pub fn submit_message(&mut self, message: &str, tag: Option<&str>) -> String

// Submit a transaction to the network.
pub fn submit_transaction(&mut self, tx: Transaction) -> String

// Check whether the node stores a certain transaction.
pub fn has_transaction(&self, hash: &str) -> bool

// Get a certain transaction.
pub fn get_transaction(&self, hash: &str) -> Option<Transaction>

// Get a certain vertex.
pub fn get_vertex(&self, hash: &str) -> Option<SharedVertex>

// Request a certain transaction from the neighbors.
pub fn request_transaction(&mut self, hash: &str) -> bool

// Add a gossip listener.
pub fn add_gossip_listener(&mut self, listener: GossipListener)

// Get a neighbor by his index.
pub fn get_neighbor_by_index(&self, index: usize) -> Neighbor

// Get current tangle statistics.
pub fn get_tangle_stats(&self) -> TangleStats

// Get all neighbors identified by their socket address.
pub fn get_neighbors(&self) -> HashMap<SocketAddr, Neighbor>

// Get the config of the node.
pub fn get_config(&self) -> Config
```
To start the `Ictarus` node we call the `run` method from `main.rs`, the entry point of the whole application. After that it will do the following steps in that order:

<img src="https://raw.githubusercontent.com/Alex6323/Ict-Architecture-In-Rust/master/images/Ictarus.png" />

An important difference to the Java implementation is, that instead of using raw threads (preemptive multitasking) the Rust implementation uses poll-based futures (cooperative multitasking), which has the benefit of being a zero-cost abstraction. This comes at the cost that now the programmer has to make sure, that all asynchronous tasks get enough time to make progress and immediatedly yield control back to the executor if they can't at the moment.


## Basic implementation of `Receiver`

`Ictarus` communicates via UDP with its neighbors. The receiving part is represented as follows:

```Rust
pub struct Receiver {
    config: SharedConfig,
    socket: UdpSocket,
    tangle: SharedTangle,
    sending_queue: SharedSendingQueue,
    request_queue: SharedRequestQueue,
    neighbors: SharedNeighbors,
    listeners: SharedListeners,
    buffer: [u8; PACKET_SIZE_WITH_REQUEST],
    waiting: HashMap<SharedKey81, HashSet<WaitingFor>>,
    deadlines: VecDeque<(SharedKey81, Instant)>,
}

// Create a new receiver.
pub fn new(config: SharedConfig, socket: UdpSocket, tangle: SharedTangle, sending_queue: SharedSendingQueue, request_queue: SharedRequestQueue, neighbors: SharedNeighbors, listeners: SharedListeners) -> Self

// Receiver is implemented as a stream of futures.
impl Stream for Receiver {
    type Item = ();
    type Error = io::Error;

    fn poll(&mut self) -> Poll<Option<()>, io::Error>
}
```

The `poll` method is responsible for handling all the incoming UDP packets. The following flow chart tries to give concise overview of what `Ictarus` is currently doing:

<img src="https://raw.githubusercontent.com/Alex6323/Ict-Architecture-In-Rust/master/images/Receiver.png" />

## Implementation of `Sender`

 The sending part of `Ictarus` is modeled like so:

```Rust
pub struct Sender {
    config: SharedConfig,
    socket: UdpSocket,
    tangle: SharedTangle,
    sending_queue: SharedSendingQueue,
    request_queue: SharedRequestQueue,
    neighbors: SharedNeighbors,
    listeners: SharedListeners,
}

// Creates a new sender.
pub fn new(config: SharedConfig, socket: UdpSocket, tangle: SharedTangle, sending_queue: SharedSendingQueue, request_queue: SharedRequestQueue, neighbors: SharedNeighbors, listeners: SharedListeners) -> Self 

impl Stream for Sender {
    type Item = ();
    type Error = io::Error;

    fn poll(&mut self) -> Poll<Option<()>, io::Error>
}
```

The `poll` method is responsible for handling all the outgoing UDP packets. The following flow chart tries to give concise overview of what `Ictarus` is currently doing:

<img src="https://raw.githubusercontent.com/Alex6323/Ict-Architecture-In-Rust/master/images/Sender.png" />

## Implementation of IOTA Transactions

In `Ictarus` an IOTA transaction looks exactly like the protocol specification for the Ict network:

```Rust
pub struct Transaction {
    pub signature_fragments: String,
    pub extra_data_digest: String,
    pub address: String,
    pub value: i64,
    pub issuance_timestamp: i64,
    pub timelock_lower_bound: i64,
    pub timelock_upper_bound: i64,
    pub bundle_nonce: String,
    pub trunk: String,
    pub branch: String,
    pub tag: String,
    pub attachment_timestamp: i64,
    pub attachment_timestamp_lower_bound: i64,
    pub attachment_timestamp_upper_bound: i64,
    pub nonce: String,
}
```

Its API provides methods to create, convert and modify transactions:

```Rust
// Create a transaction from bytes.
pub fn from_tx_bytes(bytes: &[u8]) -> Self

// Create a transaction from a tryte string 'AX9D...'.
pub fn from_tryte_string(tryte_string: &str) -> Self

// Create a transaction from trytes.
pub fn from_tx_trytes(trytes: &TxTrytes) -> Self

// Convert a transaction to bytes.
pub fn as_bytes(&self) -> TxBytes

// Convert a transaction to a tryte string 'AX9D...'.
pub fn as_tryte_string(&self) -> String

// Convert a transaction to trits.
pub fn as_trits(&self) -> TxTrits

// Convert a transaction to trytes.
pub fn as_trytes(&self) -> TxTrytes

// Get the Curl hash of the transaction.
pub fn get_hash(&self) -> Trytes81

// Store an ASCII message inside of the signature message fragment.
pub fn message(mut self, message: &str) -> Self

// Set the tag of the transaction.
pub fn tag(mut self, tag: &str) -> Self

```

## Basic implementation of the Ict Tangle

In `Ictarus` the `Tangle` is implemented very similarly to the Java version with some minor modifications, one being that the `HashMap` keys are not `String`s, but thread-safe fixed-sized arrays derived from the Curl hash of a transaction. Another difference is, that `Ictarus` stores the raw bytes, rather than the deserialized transaction object. Depending on whether the trunk and/or branch transaction is also locally available, pointers to those vertices are stored as part of a tuple struct. Furthermore metadata regarding which neighbor sent or requested a certain transaction is stored in a `unsigned byte` called `Flags`. Like the Java implementation `Ictarus` also provides partitions for vertices that share the same address or the same tag.

```Rust
pub struct Tangle {
    vertices_by_hash: HashMap<SharedKey81, (SharedVertex, Flags, MaybeTrunk, MaybeBranch)>,
    vertices_by_addr: HashMap<SharedKey81, HashSet<SharedVertex>>,
    vertices_by_tag: HashMap<SharedKey27, HashSet<SharedVertex>>,
}

pub struct Vertex {
    pub bytes: TxBytes,
    pub key: SharedKey81,
    pub addr_key: SharedKey81,
    pub tag_key: SharedKey27,
}

pub type SharedKey81 = Arc<Key81>;
pub type SharedKey27 = Arc<Key27>;
pub type Flags = u8;
pub type SharedVertex = Arc<Vertex>;
pub type MaybeTrunk = Option<SharedVertex>;
pub type MaybeBranch = Option<SharedVertex>;

```
The API for the `Tangle` representation looks like this:

```Rust
// Create a new Tangle of a fixed capacity.
pub fn new(capacity: usize) -> Self

// Get the current statistics about the Tangle.
pub fn get_stats(&self) -> TangleStats

// Marks a certain neighbor identfied by its index as sender of a certain transaction.
pub fn update_senders(&mut self, key: &Key81, sender: usize)

// Marks a certain neighbor identfied by its index as requester of a certain transaction.
pub fn update_requesters(&mut self, key: &Key81, requester: usize)

// Updates the trunk of a certain transaction.
pub fn update_trunk(&mut self, key: &Key81, trunk: SharedVertex)

// Updates the branch of a certain transaction.
pub fn update_branch(&mut self, key: &Key81, branch: SharedVertex)

// Attach a vertex to the Tangle.
pub fn attach_vertex(&mut self, tx_key: SharedKey81, vertex: Vertex, flags: Flags, trunk: MaybeTrunk, branch: MaybeBranch) -> SharedVertex

```

## Basic implementation of `Config`

The configuration of an `Ictarus` node is very basic like the early version of the Java implementation:

```Rust
pub struct Config {
    pub min_forward_delay: u64,
    pub max_forward_delay: u64,
    pub host: String,
    pub port: u16,
    pub round_duration: u64,
    pub neighbors: Vec<SocketAddr>,
}
```

Its current API looks like this:

```Rust
// Set the UDP port of the node.
pub fn set_port(&mut self, port: u16)

// Set the host name of the node.
pub fn set_host(&mut self, host: &str)

// Adds a neighbor to the config.
pub fn add_neighbor(&mut self, address: &str)

// Get the socket address of the node.
pub fn get_socket_addr(&self) -> SocketAddr

// Reads the config from file.
pub fn from_file(file: &str) -> Self 
```

Additionally there is `ConfigBuilder` type to create `Config`s easily which is straighforward and omitted to keep this document as concise as possible.

## Basic implementation of `GossipEventListener`

The analogon to interfaces in Java are `traits` to define shared behavior across types in Rust. All listeners share, that they want to get notified when transactions arrived or were sent. `Ictarus` implements this trait like so:

```Rust
pub trait GossipEventListener {
    fn on_transaction_received(&mut self, tx: &Transaction, hash: Trytes81);
    fn on_transaction_sent(&mut self, tx: &Transaction, hash: Trytes81);
}
```
To store all listeners and make them available across all threads we create a shorthand `SharedListeners` for a complex vector holding types that implement the trait `GossipEventListener`:

```Rust
pub type SharedListeners = Arc<Mutex<Vec<Box<dyn GossipEventListener + Send>>>>;

```

## Basic implementation of `Neighbor`

`Ictarus` models its neighbors by assigning a certain index, a socket address and some stats that are collected within a certain time period:

```Rust
pub struct Neighbor {
    pub index: usize,
    pub address: SocketAddr,
    pub stats: NeighborStats,
}

pub type SharedNeighbors = Arc<RwLock<HashMap<SocketAddr, Neighbor>>>;

pub struct NeighborStats {
    pub received_all: u64,
    pub received_new: u64,
    pub received_invalid: u64,
    pub prev_received_all: u64,
    pub prev_received_new: u64,
    pub prev_received_invalid: u64,
}
```

## Curl

`Ictarus` facilitates the functional version of the standard Curl implementation which is roughly twice as fast to hash incoming transactions one by one. 

## Converter functions

`Ictarus` implements all necessary functions to convert between trits, trytes, tryte strings, ascii and numbers. Those are standard implementations and omitted here for brevity.


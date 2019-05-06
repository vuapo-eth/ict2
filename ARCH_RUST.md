!!! THIS IS A DRAFT AND NOT A FINISHED DOCUMENT FOR EARLY FEEDBACK !!!

# Abstract

This document is about an early - probably the first - attempt to port the [official IOTA Ict node](https://github.com/iotaledger/ict.git) implementation written in Java to the Rust programming language. This rather basic implementation was named **Ictarus** and we will be using it for reference during this document. Its source code can be found [here](https://github.com/Alex6323/Ictarus.git). The author's intention was to get a good understanding about the inner workings of the Ict node software and at the same time deepen his knowledge about the Rust programming language. This document is intended to summarize the findings and explain some architectural deviations from the Java prototype that were necessary to end up with an idiomatically written software.

# Why using Rust for Ict

According to Wikipedia [Rust](https://en.wikipedia.org/wiki/Rust_(programming_language)) is a multi-paradigm systems programming language focused on safety, especially safe concurrency, which is syntactically similar to C++, but is designed to provide better memory safety while maintaining high performance. 

Some of Rust's most notable features are:
* compiled language
* no automated garbage collection
* statically typed (but uses type inference to reduce boilerplate)
* forced error handling
* immutable variables by default
* functional programming patterns (iterators, generators, closures)
* built-in support for tests and benchmarks
* easy toolchain management using the tool 'rustup'
* easy project management using the tool 'cargo'
* central package repository (crates.io)

What makes Rust unique though is its ability to be not only a very safe language (guaranteed memory safety, no data races) but also be on par with the most performant languages in existence today that traditionally would have been picked instead. With Rust one no longer has to sacrifice safety over performance or vice versa. 

How does Rust achieve that? First and foremost by restricting itself to only employ *zero-cost abstractions*. In simple terms that basically means, that
1. You do not "pay" for abstractions that you don't use.
2. If you use a certain abstraction you cannot get any better by hand-coding it.

 On the other hand Rust introduces some new concepts, most notably those of *Ownership and Borrowing* which are an evolutionary step forward in language design. Those concepts make whole classes of bugs in Rust impossible which plague software development since decades, and it does so without impact on performance unlike other solutions to that problem like garbage collection.

The consequence of those concepts is however, that more care has to be taken upfront when laying out the architecture. When porting software written in an OOP style to Rust one quickly realizes that some solutions cannot be reimplemented 1:1 with just some syntax changes. The Rust compiler will often times reject those attempts and for good reasons. As an example: It is not as trivial as in other languages to create self-referential objects like nodes in a linked list or vertices in a graph. Finding a good implementation here will be necessary to create a performant Tangle implementation. The final Rust Ict node will look very different internally than its Java precursor.

In the future the Ict network will be used for moving not only data but also value by utilizing  directly connected and usually weak embedded IoT devices. The programming language used to build such a system should therefore be as safe *and* as performant as possible at the same time. Rust fulfills such requirements, and therefore should be a very good fit. But it should also be noted, that Rust is still a very young language and many libraries are still in heavy development themselves. This can be expected to become less and less of a problem in the years to come.  

# About Ict

The **I**ota **c**ontrolled agen**t** (Ict) is a very flexible, module-based IOTA node. It can be very light-weight, but is not restricted to be so. Depending on the usecase it can be a very powerful node as well. It achieves this by implementing the IOTA eXtending Interface (IXI). The Ict node is specifically designed for the Internet of Things (IoT) and its diverse use cases. Targeted platforms are mainly single-board computers (SBCs) like Raspberry Pis and microcontrollers (MCUs) like the Cortex-M4 and upwards, but also more powerful platforms can be supported. To achieve this the node software is logically and programmatically separated into a core component (Ict core) and many modules implementing IXI to be able to be attached to the core. Modules are therefore usually referred to as IXI modules. 

While the Ict core implements the IOTA gossip protocol and is responsible for establishing a friend-to-friend P2P network, buffering a certain amount of gossip in a transaction store, forming and appending the Tangle, and functioning as a gateway between the p2p network and its attached modules, the modules themselves define the actual functionality and the behavior of the node. Some module might turn the node into an access point for a p2p chat application or it might turn the node into a data-specific permanode that filters and diverts data into a database. The more resources available on the target platform the node is running on, the more modules can be can be hosted by the core and the more powerful the node becomes overall. On very constrained devices however it is possible to create a very specific single-use behavior.

# Architecture of Ictarus

## Project structure
The following table should give you a basic overview about the project structure. Important types that hold most of the business logic are highlighted:

| File              | Submodule | Function                                          |
| ----------------- | --------- | ------------------------------------------------- |
| `config.rs`       | root      | type for handling node configuration              |
| `constants.rs`    | root      | central location of project wide constants        |
| `main.rs`         | root      | program entry point                               |
| `ixi.rs`          | root      | definition of the IOTA extension interface        |
| **`ictarus.rs`**  | root      | the actual node                                   |
| `listener.rs`     | network   | trait/interface definition of a gossip listener   |
| `neighbor.rs`     | network   | representation of a peer                          |
| **`receiver.rs`** | network   | handling of incoming transactions                 |
| **`sender.rs`**   | network   | handling of outgoing transactions                 |
| **`tangle.rs`**   | model     | the Tangle and Vertex datastructure               |
| `transaction.rs`  | model     | the datastructure for an IOTA transaction         |
| `time.rs`         | util      | utily functions and macros to handle time         |
| `curl.rs`         | crypto    | implementation of the Curl hashfunction           |
| `ascii.rs`        | convert   | converting other representations to ascii         |
| `bytes.rs`        | convert   | converting other representations to bytes         |
| `number.rs`       | convert   | converting other representations to numbers       |
| `trits.rs`        | convert   | converting other representations to trits         |
| `tryte_string.rs` | convert   | converting other representations to tryte strings |
| `trytes.rs`       | convert   | converting other representations to trytes        |
| `luts.rs`         | convert   | contains lookup tables for faster conversions     |

We will now in more detail describe how those highlighted modules function internally:

## Implementation of `ictarus.rs`
The basic representation of an Ictarus node looks like this:
```Rust
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
```
The `Ictarus` type holds its own **configuration** that allows to specify its overall behavior and define its neighbors, a **runtime** to start asynchronous tasks using poll-based futures, a **state**, has access to the **Tangle**, access to its **neighbors** to update their gossip stats, a **sending queue** to being able to issue transactions on its own, a **request queue** to being able to add requests on its own, and a **kill switch** mechanism to gracefully end all asychronous tasks.

The most important method on type is the **run** method. It will called from the **main** entry point of the application after reading the configuration file `ictarus.cfg` from the local file system.

```Rust
pub fn run(&mut self) -> Result<(), Box<std::error::Error>> {...}
```
It will do the following steps in that order:

1. Update the node state to `State::Initializing`.
2. Bind an UDP socket to the specified socket address. 
3. Spawn an asynchronous task responsible for listening on the socket for incoming IOTA transactions.
4. Spawn an asynchronous task responsible for sending and forwarding transactions to its neighbors as configured.
5. Spawn an asynchronous task for printing printing round stats to the terminal.
6. Update the node state to `State::Running`.

An important difference to the Java implementation is, that instead of using raw threads, the Rust implementation uses poll-based futures which are based on cooperative multitasking instead of preemptive multitasking where instead of context switching a processes voluntarily yield control when they cannot make any progress.

## Implementation of `receiver.rs`

Flow chart of what how what spawned Receiver task is doing:

<img src="https://raw.githubusercontent.com/Alex6323/Ict-Architecture-In-Rust/master/images/Receiver.png" />

## Implementation of `sender.rs`

Flow chart of what how what spawned Sender task is doing:

<img src="https://raw.githubusercontent.com/Alex6323/Ict-Architecture-In-Rust/master/images/Sender.png" />

## Implementation of `tangle.rs`

The `Tangle` datastructure looks like this:

```Rust
pub struct Tangle {
    vertices_by_hash: HashMap<SharedKey81, (SharedVertex, Flags, MaybeTrunk, MaybeBranch)>,
    vertices_by_addr: HashMap<SharedKey81, HashSet<SharedVertex>>,
    vertices_by_tag: HashMap<SharedKey27, HashSet<SharedVertex>>,
}
```
Note: In the current implementation is it used as a store of transaction or as in-memory database. In a new implementation this will become what probably will be called `TransactionStore` as it better describes it functionality. A separate type will be created and given the name `Tangle`, which then is optimized to store and update references between vertices.

The most important method of this type is `attach_vertex`. It takes a key that is derived from the transaction hash, a `Vertex`, some metadata called `flags` and maybe a reference to a `trunk` vertex and a maybe a reference to a `branch` vertex. This is achieved by using an enumeration type, that can either hold `Some(vertex)` or `None` to represent that this vertex is not available at the moment and might be updated at a later point in time.

```Rust
pub fn attach_vertex(
        &mut self,
        tx_key: SharedKey81,
        vertex: Vertex,
        flags: Flags,
        trunk: MaybeTrunk,
        branch: MaybeBranch,
    ) -> SharedVertex {...}
```






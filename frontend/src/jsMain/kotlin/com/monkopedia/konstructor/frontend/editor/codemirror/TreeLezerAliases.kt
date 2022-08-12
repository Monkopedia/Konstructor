package dukat.lezer.common

typealias ParseWrapper = (inner: PartialParse, input: Input, fragments: Array<TreeFragment>, ranges: Array<`T$32`>) -> PartialParse

typealias NodePropSource = (type: NodeType) -> dynamic

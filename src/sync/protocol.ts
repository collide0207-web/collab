import * as encoding from 'lib0/encoding'

/** Top-level message types on the wire (byte 0). Compatible with y-websocket. */
export const MESSAGE_SYNC = 0
export const MESSAGE_AWARENESS = 1
export const MESSAGE_CONTROL = 3
export const MESSAGE_PING = 8
export const MESSAGE_PONG = 9

export type Channel = 'update' | 'awareness'

/** Wrap a raw Yjs update as a SYNC/update frame. */
export function encodeSyncUpdate(update: Uint8Array): Uint8Array {
  const encoder = encoding.createEncoder()
  encoding.writeVarUint(encoder, MESSAGE_SYNC)
  // syncProtocol.messageYjsUpdate === 2
  encoding.writeVarUint(encoder, 2)
  encoding.writeVarUint8Array(encoder, update)
  return encoding.toUint8Array(encoder)
}

/** Wrap an awareness update as an AWARENESS frame. */
export function encodeAwareness(update: Uint8Array): Uint8Array {
  const encoder = encoding.createEncoder()
  encoding.writeVarUint(encoder, MESSAGE_AWARENESS)
  encoding.writeVarUint8Array(encoder, update)
  return encoding.toUint8Array(encoder)
}

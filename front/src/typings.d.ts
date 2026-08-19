/** 由 .umirc.ts 的 define 在启动时注入的本机局域网 IPv4（探测逻辑见 .umirc.ts），供 src/utils/lanIp.ts 使用 */
declare const __LAN_IP__: string | undefined;

package com.farmmacro.route;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;

/** Versioned region files containing separately deflated chunks. All calls belong on an IO worker. */
public final class ChunkCacheStore {
    private static final int MAGIC=0x4b584331, VERSION=1, MAX_BLOB=2*1024*1024, MAX_REGION=32*1024*1024;
    private final Path root,dir;
    private final long limit;
    public ChunkCacheStore(Path root,String server,String dimension,long limit) {
        this.root=root; this.dir=root.resolve(component(server)).resolve(component(dimension));
        this.limit=Math.max(1024,limit);
    }
    public Path directory() { return dir; }
    public static String component(String identity) {
        String s=identity==null ? "unknown" : identity;
        try {
            String readable=s.replaceAll("[^A-Za-z0-9._-]","_");
            if(readable.length()>48) readable=readable.substring(0,48);
            if(readable.isBlank() || readable.equals(".") || readable.equals("..")) readable="world";
            return readable+"-"+HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8))).substring(0,16);
        } catch(java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private Path file(int cx,int cz) { return dir.resolve("r."+(cx>>5)+"."+(cz>>5)+".bin"); }
    private Map<Long,byte[]> readRegion(Path f) throws IOException {
        Map<Long,byte[]> out=new LinkedHashMap<>();
        if(!Files.exists(f)) return out;
        if(Files.size(f)>MAX_REGION) throw new IOException("oversized region");
        try(DataInputStream in=new DataInputStream(new BufferedInputStream(Files.newInputStream(f)))) {
            if(in.readInt()!=MAGIC || in.readInt()!=VERSION) throw new IOException("cache version");
            int n=in.readInt(); if(n<0 || n>1024) throw new IOException("chunk count");
            for(int i=0;i<n;i++) {
                long key=in.readLong(); int size=in.readInt();
                if(size<1 || size>MAX_BLOB) throw new IOException("blob size");
                byte[] b=in.readNBytes(size); if(b.length!=size) throw new EOFException();
                if(out.put(key,b)!=null) throw new IOException("duplicate chunk");
            }
            if(in.read()!=-1) throw new IOException("trailing region data");
        }
        return out;
    }
    private static byte[] encode(CachedChunk c) throws IOException {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(DataOutputStream out=new DataOutputStream(new DeflaterOutputStream(bytes))) {
            var sections=c.sections();
            if(sections.size()>64) throw new IOException("too many sections");
            out.writeInt(sections.size());
            for(var e:sections.entrySet()) { out.writeInt(e.getKey()); for(short v:e.getValue()) out.writeShort(v); }
        }
        if(bytes.size()>MAX_BLOB) throw new IOException("chunk too large");
        return bytes.toByteArray();
    }
    private static CachedChunk decode(long key,byte[] bytes) throws IOException {
        try(DataInputStream in=new DataInputStream(new InflaterInputStream(new ByteArrayInputStream(bytes)))) {
            int n=in.readInt(); if(n<0 || n>64) throw new IOException("section count");
            Map<Integer,short[]> sections=new TreeMap<>();
            for(int i=0;i<n;i++) {
                int y=in.readInt(); if(y < -256 || y>256 || sections.containsKey(y)) throw new IOException("section coordinate");
                short[] cells=new short[4096];
                for(int j=0;j<cells.length;j++) {
                    short c=in.readShort();
                    if((c&0xf000)!=0 || (c&31)>25 || CachedChunk.hazard(c)>5) throw new IOException("invalid cell");
                    cells[j]=c;
                }
                sections.put(y,cells);
            }
            if(in.read()!=-1) throw new IOException("trailing chunk data");
            return new CachedChunk((int)(key>>32),(int)key,sections);
        }
    }
    public synchronized void put(CachedChunk c) throws IOException {
        Files.createDirectories(dir);
        Path f=file(c.x,c.z);
        Map<Long,byte[]> region;
        try { region=readRegion(f); } catch(IOException bad) { region=new LinkedHashMap<>(); }
        long chunkKey=CachedChunk.key(c.x,c.z);
        if(region.containsKey(chunkKey)) {
            try {
                var merged=decode(chunkKey,region.get(chunkKey)).sections(); merged.putAll(c.sections());
                c=new CachedChunk(c.x,c.z,merged);
            } catch(IOException corrupt) { /* replace corrupt chunk */ }
        }
        region.put(chunkKey,encode(c));
        long bytes=12+region.values().stream().mapToLong(b -> 12L+b.length).sum();
        if(bytes>MAX_REGION || bytes>limit) return;
        Path tmp=f.resolveSibling(f.getFileName()+".tmp");
        try {
            try(DataOutputStream out=new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(tmp)))) {
                out.writeInt(MAGIC); out.writeInt(VERSION); out.writeInt(region.size());
                for(var e:region.entrySet()) { out.writeLong(e.getKey()); out.writeInt(e.getValue().length); out.write(e.getValue()); }
            }
            try { Files.move(tmp,f,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
            catch(AtomicMoveNotSupportedException e) { Files.move(tmp,f,StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(tmp); }
        trim();
    }
    public synchronized Map<Long,CachedChunk> load(Set<Long> keys) {return load(keys,()->false);}
    public synchronized Map<Long,CachedChunk> load(Set<Long> keys,java.util.function.BooleanSupplier cancelled) {
        Map<Path,Set<Long>> groups=new LinkedHashMap<>();
        for(long k:keys) groups.computeIfAbsent(file((int)(k>>32),(int)k),p -> new HashSet<>()).add(k);
        Map<Long,CachedChunk> out=new HashMap<>();long decodedBytes=0;
        for(var g:groups.entrySet()) {if(cancelled.getAsBoolean()||out.size()>=128||decodedBytes>=32L*1024*1024)break;
            try {
                var r=readRegion(g.getKey());
                for(long k:g.getValue()) {if(cancelled.getAsBoolean()||out.size()>=128||decodedBytes>=32L*1024*1024)break;if(r.containsKey(k)) {
                    try {var chunk=decode(k,r.get(k));long bytes=chunk.cellBytes();if(decodedBytes+bytes>32L*1024*1024)continue;out.put(k,chunk);decodedBytes+=bytes;} catch(IOException badChunk) { /* unknown, not air */ }
                }}
                if(!r.isEmpty()) Files.setLastModifiedTime(g.getKey(),java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis()));
            } catch(IOException badRegion) { /* missing or corrupt region stays unknown */ }
        }
        return out;
    }
    public synchronized void trim() throws IOException {
        if(!Files.isDirectory(root)) return;
        List<Path> files;
        try(var s=Files.walk(root)) { files=s.filter(p -> p.getFileName().toString().matches("r\\.-?\\d+\\.-?\\d+\\.bin")).toList(); }
        List<Path> sorted=new ArrayList<>(files);
        sorted.sort(Comparator.comparingLong(p -> { try{return Files.getLastModifiedTime(p).toMillis();}catch(IOException e){return 0;} }));
        long total=0; for(Path p:sorted) total+=Files.size(p);
        for(Path p:sorted) { if(total<=limit) break; long n=Files.size(p); Files.deleteIfExists(p); total-=n; }
    }
    public static void clear(Path root) throws IOException {
        if(!Files.isDirectory(root)) return;
        try(var stream=Files.walk(root)) {
            for(Path p:stream.sorted(Comparator.reverseOrder()).toList()) if(!p.equals(root)) Files.deleteIfExists(p);
        }
    }
}

package com.jayemceekay.shadowedhearts.world.umbrafall;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.Set;
import java.util.function.IntBinaryOperator;
import java.util.function.IntConsumer;

import static com.jayemceekay.shadowedhearts.world.umbrafall.UmbrafallShapeBuilder.Material;
import static com.jayemceekay.shadowedhearts.world.umbrafall.UmbrafallShapeBuilder.Point;
import static com.jayemceekay.shadowedhearts.world.umbrafall.UmbrafallShapeBuilder.StructuralLayer;

/** Shared varied anatomy; each saved profile retains its body, deposits and surface distribution. */
final class UmbrafallVariedShape {
    private enum Form { BOULDER, WEDGE, ELONGATED, CLUSTERED }
    private record Lobe(double u, double y, double v, double ru, double ry, double rv) {
        double distance(double x, double dy, double z) {
            return square((x-u)/ru) + square((dy-y)/ry) + square((z-v)/rv);
        }
    }
    private record Vein(double ax, double ay, double az, double bx, double by, double bz, double radius) {
        boolean contains(double x, double y, double z) {
            double dx=bx-ax, dy=by-ay, dz=bz-az;
            double length=dx*dx+dy*dy+dz*dz;
            double t=length == 0 ? 0 : Math.clamp(((x-ax)*dx+(y-ay)*dy+(z-az)*dz)/length, 0, 1);
            return square(x-ax-dx*t)+square(y-ay-dy*t)+square(z-az-dz*t) <= radius*radius;
        }
    }
    @FunctionalInterface private interface CellTest { boolean contains(int x, int dy, int z); }
    private record RankedCell(int index, double score, long tieBreak) { }
    private record Deposits(BitSet minerals, BitSet crystals) { }
    private record FloorCell(Point point, double score, long tieBreak) { }

    private final long seed;
    private final boolean layeredBody, shellDebris, expeditionDeposits, stratified, seamedDeposits, activeCore;
    private final boolean scatteredSurface;
    private final Form form;
    private final int radius, depth, coreY;
    private final double r, height, ru, rv, exponent, angle, cos, sin, shearU, shearV;
    private final double craterAlong, craterAcross, craterBase, craterPhase, bowlPower, bankHeight, bankBias;
    private final double rimGapAngle, rimGapStrength, fractureAngle, fractureOffset, fractureWidth;
    private final boolean fracture;
    private final int debrisSpacing, horizontalLimit, verticalLimit, width, layerSize;
    private final List<Lobe> lobes;
    private final List<Vein> branches;
    private final BitSet mass, veins, crystals;
    private final int[] surfaceDepth;
    private final BitSet coreCasing, crackedExterior;
    private List<Point> starterDeposit = List.of();
    private Set<Point> starterCells = Set.of();
    private Set<Point> debrisCells = Set.of();

    UmbrafallVariedShape(UmbrafallTier tier, long seed, UmbrafallGeometry geometry) {
        this.seed=seed;
        boolean budgeted=geometry.usesBudgetedBody();
        expeditionDeposits=geometry.usesExpeditionDeposits();
        stratified=geometry.usesStratifiedLayers();
        seamedDeposits=geometry.usesSeamedDeposits();
        scatteredSurface=geometry.usesScatteredSurface();
        activeCore=tier.hasActiveCore();
        shellDebris=tier == UmbrafallTier.FAINT && geometry.usesLayeredFaint();
        layeredBody=tier.hasActiveCore() || shellDebris;
        radius=geometry.radius(tier); depth=geometry.craterDepth(tier); r=geometry.bodyRadius(tier);
        Random random=new Random(mix(seed ^ 0xD1B54A32D192ED03L));
        form=Form.values()[random.nextInt(Form.values().length)];
        angle=random.nextDouble()*Math.PI*2; cos=Math.cos(angle); sin=Math.sin(angle);
        shearU=between(random,-0.20,0.20); shearV=between(random,-0.12,0.12);
        double h;
        switch (form) {
            case BOULDER -> {
                ru=r*between(random,0.85,1.05); rv=r*between(random,0.82,1.04);
                h=r*between(random,0.72,0.94); exponent=2;
            }
            case WEDGE -> {
                ru=r*between(random,1.00,1.22); rv=r*between(random,0.72,0.88);
                h=r*between(random,0.58,0.78); exponent=between(random,2.6,3.6);
            }
            case ELONGATED -> {
                ru=r*between(random,1.30,1.50); rv=r*between(random,0.50,0.64);
                h=r*between(random,0.42,0.56); exponent=2;
            }
            case CLUSTERED -> {
                ru=r*between(random,0.63,0.78); rv=r*between(random,0.63,0.78);
                h=r*between(random,0.58,0.76); exponent=2;
            }
            default -> throw new IllegalStateException();
        }
        height=Math.max(2,h);
        coreY=-depth+(int)Math.ceil(height*0.20);
        var parts=new ArrayList<Lobe>();
        int count=form == Form.CLUSTERED ? 2+random.nextInt(3) : random.nextInt(3);
        for (int i=0; i<count; i++) {
            double direction=i*Math.PI*2/Math.max(1,count)+between(random,-0.8,0.8);
            double offset=r*(form == Form.CLUSTERED ? between(random,0.65,0.95) : between(random,0.30,0.55));
            double size=r*(form == Form.CLUSTERED ? between(random,0.48,0.72) : between(random,0.34,0.52));
            parts.add(new Lobe(Math.cos(direction)*offset, height*between(random,-0.15,0.18),
                    Math.sin(direction)*offset, size, height*between(random,0.60,1.04), size*between(random,0.72,1.10)));
        }
        lobes=List.copyOf(parts);
        // Only a minority of angular/elongated bodies have one local fracture. Never a universal slit.
        fracture=(form == Form.WEDGE || form == Form.ELONGATED) && random.nextDouble()<0.25;
        fractureAngle=between(random,-0.8,0.8); fractureOffset=r*between(random,0.50,0.80);
        fractureWidth=r*between(random,0.055,0.10);
        craterAlong=form == Form.ELONGATED ? 1.10 : between(random,0.95,1.06);
        craterAcross=form == Form.ELONGATED ? between(random,0.80,0.87) : between(random,0.87,1.02);
        craterBase=between(random,0.68,0.72); craterPhase=random.nextDouble()*Math.PI*2;
        bowlPower=between(random,1.55,2.65); bankHeight=between(random,0.12,0.27); bankBias=between(random,0,0.55);
        rimGapAngle=random.nextDouble()*Math.PI*2;
        rimGapStrength=random.nextDouble()<0.35 ? between(random,0.4,0.95) : 0;
        debrisSpacing=28+random.nextInt(45);
        branches=buildBranches(random);
        // All analytic lobes and shears fit well within this small body-only volume.
        int horizontal=(int)Math.ceil(r*2.1+height*0.3);
        horizontalLimit=budgeted ? Math.min(radius,horizontal) : horizontal;
        verticalLimit=budgeted && tier.hasActiveCore() ? Math.max(3,(int)Math.ceil(height*1.30)) : (int)Math.ceil(height*1.30);
        width=horizontalLimit*2+1; layerSize=width*width;
        mass=budgeted ? budgetedMass(tier,geometry) : connected(this::rawBody);
        surfaceDepth=stratified ? surfaceDepths() : null;
        coreCasing=stratified ? nestedCoreCasing() : new BitSet();
        BitSet mineralCells;
        if (stratified) {
            Deposits deposits=stratifiedDeposits(geometry.crystalBlocks(tier));
            mineralCells=deposits.minerals();
            crystals=deposits.crystals();
        } else if (expeditionDeposits) {
            Deposits deposits=budgetedDeposits(tier,geometry);
            mineralCells=deposits.minerals();
            crystals=deposits.crystals();
        } else {
            mineralCells=layeredBody ? connected(this::rawVein) : new BitSet();
            crystals=new BitSet();
        }
        // Keep V8's mineral mask for crack selection so only the vein distribution changes in V9.
        crackedExterior=stratified ? sparseExteriorCracks(mineralCells) : new BitSet();
        veins=geometry.usesSparseVeins() ? sparseVeinPatches(tier,mineralCells) : mineralCells;
    }

    String morphology() { return form.name().toLowerCase(Locale.ROOT); }
    boolean hasMajorFracture() { return fracture; }
    double height() { return height; }
    int minBodyY() { return coreY-verticalLimit; }
    int bodyBlockCount() { return mass.cardinality(); }
    boolean isBodyBlock(int x,int y,int z) { return inside(mass,x,y-coreY,z); }
    List<Point> starterDepositOffsets() { return starterDeposit; }

    /** Select exposed crater-floor cells after the caller has fitted the saved terrain. */
    void configureStarterDeposit(IntBinaryOperator columnShift) {
        if (scatteredSurface) {
            configureScatteredResidue(columnShift);
            return;
        }
        double direction=(mix(seed ^ 0x8CB92BA72F3D8DD7L)>>>11)*0x1.0p-53*Math.PI*2;
        double ax=Math.cos(direction)*(r+1),az=Math.sin(direction)*(r+1);
        var candidates=new HashMap<Long,FloorCell>();
        var ordered=new ArrayList<FloorCell>();
        Comparator<FloorCell> order=Comparator.comparingDouble(FloorCell::score)
                .thenComparingLong(FloorCell::tieBreak).thenComparingInt(cell->cell.point().x())
                .thenComparingInt(cell->cell.point().z());
        for (int z=-radius;z<=radius;z++) for (int x=-radius;x<=radius;x++) {
            double radial=craterRadial(x,z);
            if (radial>=0.90) continue;
            int shift=columnShift.applyAsInt(x,z);
            int y=(int)Math.floor(-depth*(1-Math.pow(radial,bowlPower)))+shift;
            if (isBodyBlock(x,y-1,z) || isBodyBlock(x,y,z) || isBodyBlock(x,y+1,z)
                    || terrainMaterialAt(x,y+1,z,shift)!=Material.AIR) continue;
            Point point=new Point(x,y,z);
            FloorCell cell=new FloorCell(point,square(x-ax)+square(z-az),hash(x,y,z));
            candidates.put(columnKey(x,z),cell); ordered.add(cell);
        }
        ordered.sort(order);
        if (ordered.size()<12) throw new IllegalStateException("Umbrafall Faint crater cannot fit its starter deposit");
        var pending=new PriorityQueue<FloorCell>(order);
        var queued=new HashSet<Long>();
        var selected=new ArrayList<Point>(12);
        int next=0;
        while (selected.size()<12) {
            if (pending.isEmpty()) {
                while (queued.contains(columnKey(ordered.get(next).point().x(),ordered.get(next).point().z()))) next++;
                FloorCell start=ordered.get(next++);
                queued.add(columnKey(start.point().x(),start.point().z())); pending.add(start);
            }
            FloorCell cell=pending.remove();
            Point point=cell.point(); selected.add(point);
            int[][] steps={{1,0},{-1,0},{0,1},{0,-1}};
            for (int[] step:steps) {
                long key=columnKey(point.x()+step[0],point.z()+step[1]);
                FloorCell neighbour=candidates.get(key);
                if (neighbour!=null && queued.add(key)) pending.add(neighbour);
            }
        }
        starterDeposit=List.copyOf(selected);
        starterCells=Set.copyOf(selected);
    }

    /** V10 replaces the connected starter patch and random Faint floor residue with six spaced cells. */
    private void configureScatteredResidue(IntBinaryOperator columnShift) {
        var candidates=new ArrayList<Point>();
        for (int z=-radius;z<=radius;z++) for (int x=-radius;x<=radius;x++) {
            double radial=craterRadial(x,z);
            if (radial>=0.90) continue;
            int shift=columnShift.applyAsInt(x,z);
            int y=(int)Math.floor(-depth*(1-Math.pow(radial,bowlPower)))+shift;
            if (isBodyBlock(x,y-1,z) || isBodyBlock(x,y,z) || isBodyBlock(x,y+1,z)
                    || terrainMaterialAt(x,y+1,z,shift)!=Material.AIR) continue;
            candidates.add(new Point(x,y,z));
        }
        starterDeposit=separatedSurfaceCells(candidates,6,true);
        starterCells=Set.copyOf(starterDeposit);
    }

    /** Plan visible, separated pebble piles beyond the rim, fitted to the surveyed original ground. */
    void configureSurfaceDebris(UmbrafallTier tier,IntBinaryOperator columnShift) {
        if (!scatteredSurface) return;
        int target=switch (tier) {
            case FAINT -> 6;
            case WEAK -> 10;
            case MODERATE -> 14;
            case STRONG -> 20;
            case RESONANT -> 28;
        };
        var candidates=new ArrayList<Point>();
        for (int z=-radius;z<=radius;z++) for (int x=-radius;x<=radius;x++) {
            if (craterRadial(x,z)<=1.18 || x*(long)x+z*(long)z>radius*(long)radius) continue;
            // Surveying uses the flat plan first. Keep chosen columns identical after terrain fitting.
            boolean bodyColumn=false;
            for (int dy=-verticalLimit;dy<=verticalLimit;dy++) {
                if (inside(mass,x,dy,z)) { bodyColumn=true; break; }
            }
            if (bodyColumn) continue;
            int y=1+columnShift.applyAsInt(x,z);
            candidates.add(new Point(x,y,z));
        }
        debrisCells=Set.copyOf(separatedSurfaceCells(candidates,target,false));
    }

    private List<Point> separatedSurfaceCells(List<Point> candidates,int target,boolean spreadAcrossFloor) {
        candidates.sort((a,b)->{
            int order=Long.compareUnsigned(hash(a.x(),0,a.z()),hash(b.x(),0,b.z()));
            if (order==0) order=Integer.compare(a.x(),b.x());
            return order==0 ? Integer.compare(a.z(),b.z()) : order;
        });
        var selected=new ArrayList<Point>(target);
        while (selected.size()<target) {
            Point best=null;
            long bestDistance=-1;
            for (Point candidate:candidates) {
                long nearest=Long.MAX_VALUE;
                boolean separated=true;
                for (Point placed:selected) {
                    int dx=Math.abs(candidate.x()-placed.x()),dz=Math.abs(candidate.z()-placed.z());
                    if (Math.max(dx,dz)<=1) { separated=false; break; }
                    nearest=Math.min(nearest,dx*(long)dx+dz*(long)dz);
                }
                if (!separated) continue;
                if (!spreadAcrossFloor) { best=candidate; break; }
                if (nearest>bestDistance) { best=candidate; bestDistance=nearest; }
            }
            if (best==null) break;
            selected.add(best);
        }
        return List.copyOf(selected);
    }

    private static long columnKey(int x,int z) { return ((long)x<<32) ^ (z&0xffffffffL); }

    /** Erode the actual irregular mass, so every route inward crosses the same nested boundaries. */
    private int[] surfaceDepths() {
        int[] result=new int[layerSize*(verticalLimit*2+1)];
        var pending=new ArrayDeque<Integer>();
        for (int cell=mass.nextSetBit(0);cell>=0;cell=mass.nextSetBit(cell+1)) {
            int x=cell%width-horizontalLimit,dy=cell/layerSize-verticalLimit,z=cell/width%width-horizontalLimit;
            if (!inside(mass,x-1,dy,z) || !inside(mass,x+1,dy,z)
                    || !inside(mass,x,dy-1,z) || !inside(mass,x,dy+1,z)
                    || !inside(mass,x,dy,z-1) || !inside(mass,x,dy,z+1)) {
                result[cell]=1; pending.add(cell);
            }
        }
        while (!pending.isEmpty()) {
            int cell=pending.remove();
            forEachNeighbour(cell,next->{
                if (mass.get(next) && result[next]==0) {
                    result[next]=result[cell]+1; pending.add(next);
                }
            });
        }
        return result;
    }

    private BitSet sparseExteriorCracks(BitSet mineralCells) {
        var candidates=new ArrayList<Integer>();
        for (int cell=mass.nextSetBit(0);cell>=0;cell=mass.nextSetBit(cell+1)) {
            if (surfaceDepth[cell]==1 && !mineralCells.get(cell)) candidates.add(cell);
        }
        candidates.sort((a,b)->{
            int order=Long.compareUnsigned(cellHash(a),cellHash(b));
            return order==0 ? Integer.compare(a,b) : order;
        });
        int target=Math.min(candidates.size(),Math.max(1,(int)Math.round(candidates.size()*0.05)));
        var result=new BitSet();
        for (int i=0;i<target;i++) result.set(candidates.get(i));
        return result;
    }

    /** A few isolated singles or face-connected pairs, independent of the historical crystal budget. */
    private BitSet sparseVeinPatches(UmbrafallTier tier,BitSet mineralCells) {
        var result=new BitSet();
        // A separate stream keeps body, terrain and crystal placement identical to V8.
        Random random=new Random(mix(seed ^ 0xA24BAED4963EE407L));
        double occurrence=switch (tier) {
            case FAINT -> 0.02;
            case WEAK -> 0.05;
            case MODERATE -> 0.50;
            case STRONG -> 0.75;
            case RESONANT -> 1.0;
        };
        if (random.nextDouble()>=occurrence) return result;
        int patches=switch (tier) {
            case FAINT, WEAK -> 1;
            case MODERATE -> 1+random.nextInt(2);
            case STRONG, RESONANT -> 2+random.nextInt(2);
        };
        // Thin the existing deposits so small bands retain their original structural safeguards.
        BitSet available=(BitSet)mineralCells.clone();
        available.andNot(crystals);
        available.andNot(crackedExterior);
        if (activeCore) available.clear(index(0,0,0));
        for (int patch=0;patch<patches && !available.isEmpty();patch++) {
            int ordinal=random.nextInt(available.cardinality());
            int anchor=available.nextSetBit(0);
            for (int i=0;i<ordinal;i++) anchor=available.nextSetBit(anchor+1);
            result.set(anchor);
            int partner=-1;
            if (random.nextBoolean()) {
                var neighbours=new ArrayList<Integer>(6);
                forEachNeighbour(anchor,next->{ if (available.get(next)) neighbours.add(next); });
                if (!neighbours.isEmpty()) {
                    partner=neighbours.get(random.nextInt(neighbours.size()));
                    result.set(partner);
                }
            }
            excludePatchNeighbours(available,anchor);
            if (partner>=0) excludePatchNeighbours(available,partner);
        }
        return result;
    }

    /** Keep distinct patches apart even at edges and corners, including across structural bands. */
    private void excludePatchNeighbours(BitSet available,int cell) {
        int x=cell%width,z=cell/width%width,y=cell/layerSize;
        for (int dy=Math.max(0,y-1);dy<=Math.min(verticalLimit*2,y+1);dy++) {
            for (int dz=Math.max(0,z-1);dz<=Math.min(width-1,z+1);dz++) {
                for (int dx=Math.max(0,x-1);dx<=Math.min(width-1,x+1);dx++) {
                    available.clear(dy*layerSize+dz*width+dx);
                }
            }
        }
    }

    /** Keep only the deep enclosure connected to the centre; remote lobes remain mantle. */
    private BitSet nestedCoreCasing() {
        var result=new BitSet();
        int centre=index(0,0,0);
        // Small inert Faint bodies can lack room for a third layer; never enlarge their body or activate a core.
        if (surfaceDepth[centre]<3) return result;
        var pending=new ArrayDeque<Integer>();
        result.set(centre); pending.add(centre);
        while (!pending.isEmpty()) forEachNeighbour(pending.remove(),next->{
            if (surfaceDepth[next]>=3 && !result.get(next)) { result.set(next); pending.add(next); }
        });
        return result;
    }

    StructuralLayer structuralLayerAt(int x,int y,int z) {
        int dy=y-coreY;
        if (!stratified || !inside(mass,x,dy,z) || (activeCore && x==0 && dy==0 && z==0)) {
            return StructuralLayer.NONE;
        }
        int cell=index(x,dy,z);
        if (coreCasing.get(cell)) return StructuralLayer.CORE_CASING;
        return surfaceDepth[cell]==1 ? StructuralLayer.OUTER_SHELL : StructuralLayer.LATTICE_MANTLE;
    }

    /** Preserve V6's total mineral budget, but spread small deposits across the actual structural bands. */
    private Deposits stratifiedDeposits(int crystalTarget) {
        BitSet[] bands={new BitSet(),new BitSet(),new BitSet()};
        for (int cell=mass.nextSetBit(0);cell>=0;cell=mass.nextSetBit(cell+1)) {
            if (activeCore && cell==index(0,0,0)) continue;
            int band=coreCasing.get(cell) ? 2 : surfaceDepth[cell]==1 ? 0 : 1;
            bands[band].set(cell);
        }
        double fraction=0.08+0.04*((mix(seed ^ 0xDB4F0B9175AE2165L)>>>11)*0x1.0p-53);
        int target=(int)Math.round(mass.cardinality()*fraction);
        int[] quotas=new int[3];
        int total=0,available=mass.cardinality()-(activeCore ? 1 : 0);
        for (int band=0;band<3;band++) {
            int capacity=bands[band].cardinality()/2;
            quotas[band]=capacity==0 ? 0 : Math.min(capacity,Math.max(1,target*bands[band].cardinality()/available));
            total+=quotas[band];
        }
        while (total>target) {
            int chosen=-1;
            for (int band=0;band<3;band++) if (quotas[band]>1 && (chosen<0 || quotas[band]>quotas[chosen])) chosen=band;
            if (chosen<0) throw new IllegalStateException("Umbrafall mineral budget cannot cover its structural bands");
            quotas[chosen]--; total--;
        }
        while (total<target) {
            int chosen=-1;
            double greatestDeficit=Double.NEGATIVE_INFINITY;
            for (int band=0;band<3;band++) {
                if (quotas[band]>=bands[band].cardinality()/2) continue;
                double deficit=target*(double)bands[band].cardinality()/available-quotas[band];
                if (deficit>greatestDeficit) { chosen=band; greatestDeficit=deficit; }
            }
            if (chosen<0) throw new IllegalStateException("Umbrafall structural bands cannot fit the mineral budget");
            quotas[chosen]++; total++;
        }
        int[] crystalQuotas=new int[3];
        int start=Math.floorMod((int)mix(seed ^ 0x8CB92BA72F3D8DD7L),3);
        for (int left=crystalTarget;left>0;) {
            boolean assigned=false;
            for (int offset=0;offset<3 && left>0;offset++) {
                int band=(start+offset)%3;
                if (crystalQuotas[band]<quotas[band]) { crystalQuotas[band]++; left--; assigned=true; }
            }
            if (!assigned) throw new IllegalStateException("Umbrafall crystal budget exceeds its mineral budget");
        }
        var minerals=new BitSet();
        var crystalCells=new BitSet();
        for (int band=0;band<3;band++) {
            BitSet deposit=seamedDeposits ? growBandSeams(bands[band],quotas[band])
                    : growBandDeposit(bands[band],quotas[band]);
            minerals.or(deposit);
            addStratifiedCrystals(deposit,crystalQuotas[band],crystalCells);
        }
        return new Deposits(minerals,crystalCells);
    }

    /** Short, separated paths keep the thin shell's mineral quota from spreading into a surface sheet. */
    private BitSet growBandSeams(BitSet allowed,int target) {
        var result=new BitSet();
        var anchors=new ArrayList<Integer>();
        while (result.cardinality()<target) {
            BitSet starts=(BitSet)allowed.clone();
            starts.andNot(result);
            for (int cell=result.nextSetBit(0);cell>=0;cell=result.nextSetBit(cell+1)) {
                forEachNeighbour(cell,starts::clear);
            }
            int current=depositAnchor(starts,result,anchors);
            anchors.add(current);
            int length=Math.min(target-result.cardinality(),3+(int)Math.floorMod(cellHash(current),3L));
            int previous=-1;
            for (int step=0;step<length;step++) {
                result.set(current);
                int next=nextSeamCell(current,previous,allowed,result);
                if (next<0) break;
                previous=current;
                current=next;
            }
        }
        return result;
    }

    private int nextSeamCell(int current,int previous,BitSet allowed,BitSet occupied) {
        int[] best={-1};
        double[] bestScore={Double.NEGATIVE_INFINITY};
        forEachNeighbour(current,next->{
            if (!allowed.get(next) || occupied.get(next)) return;
            int[] neighbours={0};
            forEachNeighbour(next,cell->{ if (occupied.get(cell)) neighbours[0]++; });
            // Touch only the current tip, so paths cannot widen, loop or join another seam in this band.
            if (neighbours[0]!=1) return;
            double score=(cellHash(next)>>>11)*0x1.0p-53;
            if (previous>=0 && next-current==current-previous) score+=1;
            if (score>bestScore[0] || (score==bestScore[0] && next<best[0])) {
                best[0]=next;
                bestScore[0]=score;
            }
        });
        return best[0];
    }

    private BitSet growBandDeposit(BitSet allowed,int target) {
        var result=new BitSet();
        var queued=new BitSet();
        var anchors=new ArrayList<Integer>();
        var pending=new PriorityQueue<RankedCell>(Comparator.comparingDouble(RankedCell::score)
                .thenComparingLong(RankedCell::tieBreak).thenComparingInt(RankedCell::index));
        int anchor=-1;
        while (result.cardinality()<target) {
            if (pending.isEmpty()) {
                anchor=depositAnchor(allowed,result,anchors); anchors.add(anchor);
                queueDepositCell(anchor,anchor,allowed,result,queued,pending);
            }
            int cell=pending.remove().index(); result.set(cell);
            int currentAnchor=anchor;
            forEachNeighbour(cell,next->queueDepositCell(next,currentAnchor,allowed,result,queued,pending));
        }
        return result;
    }

    /** A thin casing may consist of separate solid faces; keep crystal patches inside each component. */
    private void addStratifiedCrystals(BitSet deposit,int target,BitSet result) {
        var remaining=(BitSet)deposit.clone();
        while (target>0) {
            var largest=new BitSet();
            var seen=new BitSet();
            for (int start=remaining.nextSetBit(0);start>=0;start=remaining.nextSetBit(start+1)) {
                if (seen.get(start)) continue;
                var component=new BitSet();
                var pending=new ArrayDeque<Integer>();
                component.set(start); seen.set(start); pending.add(start);
                while (!pending.isEmpty()) forEachNeighbour(pending.remove(),next->{
                    if (remaining.get(next) && !seen.get(next)) { seen.set(next); component.set(next); pending.add(next); }
                });
                if (component.cardinality()>largest.cardinality()) largest=component;
            }
            if (largest.isEmpty()) throw new IllegalStateException("Umbrafall crystal patch cannot fit its structural band");
            int count=Math.min(target,Math.min(3,largest.cardinality()));
            addCrystalPocket(largest,count,result);
            remaining.andNot(result);
            target-=count;
        }
    }

    /** A seed controls 8–12% mineral occupancy; pockets are face-grown compact solid deposits. */
    private Deposits budgetedDeposits(UmbrafallTier tier,UmbrafallGeometry geometry) {
        BitSet allowed=(BitSet)mass.clone();
        if (tier.hasActiveCore()) allowed.clear(index(0,0,0));
        double fraction=0.08+0.04*((mix(seed ^ 0xDB4F0B9175AE2165L)>>>11)*0x1.0p-53);
        int target=(int)Math.round(mass.cardinality()*fraction);
        int crystalTarget=geometry.crystalBlocks(tier);
        int pockets=switch(tier) { case FAINT,WEAK->1; case MODERATE,STRONG->2; case RESONANT->3; };
        var minerals=new BitSet();
        var crystalCells=new BitSet();
        var anchors=new ArrayList<Integer>();
        for (int pocket=0;pocket<pockets;pocket++) {
            int size=target/pockets+(pocket<target%pockets ? 1 : 0);
            int crystalSize=crystalTarget/pockets+(pocket<crystalTarget%pockets ? 1 : 0);
            var local=new BitSet();
            var queued=new BitSet();
            var pending=new PriorityQueue<RankedCell>(Comparator.comparingDouble(RankedCell::score)
                    .thenComparingLong(RankedCell::tieBreak).thenComparingInt(RankedCell::index));
            int anchor=depositAnchor(allowed,minerals,anchors);
            anchors.add(anchor);
            while (local.cardinality()<size) {
                if (pending.isEmpty()) {
                    int start=local.isEmpty() ? anchor : depositAnchor(allowed,minerals,anchors);
                    queueDepositCell(start,anchor,allowed,minerals,queued,pending);
                }
                int cell=pending.remove().index();
                minerals.set(cell); local.set(cell);
                forEachNeighbour(cell,neighbour->queueDepositCell(neighbour,anchor,allowed,minerals,queued,pending));
            }
            addCrystalPocket(local,crystalSize,crystalCells);
        }
        return new Deposits(minerals,crystalCells);
    }

    private int depositAnchor(BitSet allowed,BitSet occupied,List<Integer> previous) {
        int best=-1;
        boolean bestExposed=false;
        double bestSeparation=-1;
        long bestHash=0;
        for (int cell=allowed.nextSetBit(0);cell>=0;cell=allowed.nextSetBit(cell+1)) {
            if (occupied.get(cell)) continue;
            boolean exposed=exposedBodyCell(cell);
            double separation=Double.POSITIVE_INFINITY;
            for (int anchor:previous) separation=Math.min(separation,cellDistance(cell,anchor));
            long detail=cellHash(cell);
            if (best<0 || (previous.isEmpty() && exposed && !bestExposed)
                    || (!(previous.isEmpty() && !exposed && bestExposed)
                    && (separation>bestSeparation || (separation==bestSeparation && Long.compareUnsigned(detail,bestHash)<0)))) {
                best=cell; bestExposed=exposed; bestSeparation=separation; bestHash=detail;
            }
        }
        if (best<0) throw new IllegalStateException("Umbrafall mineral budget exceeds its body");
        return best;
    }

    private boolean exposedBodyCell(int cell) {
        int x=cell%width-horizontalLimit,z=cell/width%width-horizontalLimit;
        int y=cell/layerSize-verticalLimit+coreY;
        int[][] steps={{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
        for (int[] step:steps) {
            int nx=x+step[0],ny=y+step[1],nz=z+step[2];
            if (isBodyBlock(nx,ny,nz)) continue;
            Material material=terrainMaterialAt(nx,ny,nz,0);
            if (material==Material.AIR || (ny>0 && material==Material.KEEP)) return true;
        }
        return false;
    }

    private void queueDepositCell(int cell,int anchor,BitSet allowed,BitSet occupied,BitSet queued,
                                  PriorityQueue<RankedCell> pending) {
        if (!allowed.get(cell) || occupied.get(cell) || queued.get(cell)) return;
        queued.set(cell);
        long detail=cellHash(cell);
        double variation=(detail>>>11)*0x1.0p-53*0.35;
        pending.add(new RankedCell(cell,cellDistance(cell,anchor)+variation,detail));
    }

    private double cellDistance(int a,int b) {
        return square(a%width-b%width)+square(a/width%width-b/width%width)+square(a/layerSize-b/layerSize);
    }

    private long cellHash(int cell) {
        return hash(cell%width-horizontalLimit,cell/layerSize-verticalLimit+coreY,cell/width%width-horizontalLimit);
    }

    /** Place each crystal reward in one compact connected cluster within its mineral deposit. */
    private void addCrystalPocket(BitSet deposit,int target,BitSet result) {
        BitSet largest=new BitSet(),seen=new BitSet();
        for (int start=deposit.nextSetBit(0);start>=0;start=deposit.nextSetBit(start+1)) {
            if (seen.get(start)) continue;
            var component=new BitSet();
            var pending=new ArrayDeque<Integer>();
            seen.set(start); component.set(start); pending.add(start);
            while (!pending.isEmpty()) forEachNeighbour(pending.remove(),cell->{
                if (deposit.get(cell) && !seen.get(cell)) { seen.set(cell); component.set(cell); pending.add(cell); }
            });
            if (component.cardinality()>largest.cardinality()) largest=component;
        }
        if (largest.cardinality()<target) throw new IllegalStateException("Umbrafall crystal pocket cannot fit its deposit");
        double cx=0,cy=0,cz=0;
        for (int cell=largest.nextSetBit(0);cell>=0;cell=largest.nextSetBit(cell+1)) {
            cx+=cell%width; cy+=cell/layerSize; cz+=cell/width%width;
        }
        cx/=largest.cardinality(); cy/=largest.cardinality(); cz/=largest.cardinality();
        int anchor=-1; double score=Double.POSITIVE_INFINITY;
        for (int cell=largest.nextSetBit(0);cell>=0;cell=largest.nextSetBit(cell+1)) {
            double distance=square(cell%width-cx)+square(cell/layerSize-cy)+square(cell/width%width-cz);
            if (distance<score) { anchor=cell; score=distance; }
        }
        var pending=new PriorityQueue<RankedCell>(Comparator.comparingDouble(RankedCell::score)
                .thenComparingLong(RankedCell::tieBreak).thenComparingInt(RankedCell::index));
        var queued=new BitSet();
        var selected=new BitSet();
        queueDepositCell(anchor,anchor,largest,selected,queued,pending);
        while (selected.cardinality()<target) {
            int cell=pending.remove().index(); selected.set(cell); result.set(cell);
            int center=anchor;
            BitSet candidates=largest;
            forEachNeighbour(cell,next->queueDepositCell(next,center,candidates,selected,queued,pending));
        }
    }

    private void forEachNeighbour(int cell,IntConsumer visitor) {
        int x=cell%width,z=cell/width%width,y=cell/layerSize;
        if (x>0) visitor.accept(cell-1);
        if (x+1<width) visitor.accept(cell+1);
        if (z>0) visitor.accept(cell-width);
        if (z+1<width) visitor.accept(cell+width);
        if (y>0) visitor.accept(cell-layerSize);
        if (y<verticalLimit*2) visitor.accept(cell+layerSize);
    }

    /** Grow the seed's form from the core by field score, keeping every added voxel face-connected. */
    private BitSet budgetedMass(UmbrafallTier tier,UmbrafallGeometry geometry) {
        double variation=0.90+0.20*((mix(seed ^ 0xA24BAED4963EE407L)>>>11)*0x1.0p-53);
        int target=Math.min(geometry.maxBodyBlocks(),(int)Math.round(geometry.bodyTarget(tier)*variation));
        int volume=layerSize*(verticalLimit*2+1);
        BitSet result=new BitSet(volume),queued=new BitSet(volume);
        if (tier.hasActiveCore()) {
            // A discrete radius-three sphere protects the core even in short or narrow forms.
            for (int dy=-3;dy<=3;dy++) for (int z=-3;z<=3;z++) for (int x=-3;x<=3;x++)
                if (x*x+dy*dy+z*z<=9) result.set(index(x,dy,z));
        } else result.set(index(0,0,0));
        if (result.cardinality()>target) throw new IllegalStateException("Umbrafall budget cannot cover its core");
        var pending=new PriorityQueue<RankedCell>(Comparator.comparingDouble(RankedCell::score)
                .thenComparingLong(RankedCell::tieBreak).thenComparingInt(RankedCell::index));
        for (int cell=result.nextSetBit(0);cell>=0;cell=result.nextSetBit(cell+1))
            enqueueNeighbours(cell,result,queued,pending);
        int count=result.cardinality();
        while (count<target) {
            RankedCell next=pending.poll();
            if (next==null) throw new IllegalStateException("Umbrafall body exceeds its geometry bounds");
            result.set(next.index()); count++;
            enqueueNeighbours(next.index(),result,queued,pending);
        }
        return result;
    }

    private void enqueueNeighbours(int cell,BitSet result,BitSet queued,PriorityQueue<RankedCell> pending) {
        int x=cell%width,z=cell/width%width,y=cell/layerSize;
        if (x>0) enqueue(cell-1,result,queued,pending);
        if (x+1<width) enqueue(cell+1,result,queued,pending);
        if (z>0) enqueue(cell-width,result,queued,pending);
        if (z+1<width) enqueue(cell+width,result,queued,pending);
        if (y>0) enqueue(cell-layerSize,result,queued,pending);
        if (y<verticalLimit*2) enqueue(cell+layerSize,result,queued,pending);
    }

    private void enqueue(int cell,BitSet result,BitSet queued,PriorityQueue<RankedCell> pending) {
        if (result.get(cell)||queued.get(cell)) return;
        queued.set(cell);
        int x=cell%width-horizontalLimit,dy=cell/layerSize-verticalLimit,z=cell/width%width-horizontalLimit;
        pending.add(new RankedCell(cell,bodyScore(x,dy,z),hash(x,dy+coreY,z)));
    }

    /** Continuous form field lets small bodies vary without integer-radius jumps or traversal bias. */
    private double bodyScore(int x,int dy,int z) {
        double u=x*cos+z*sin-dy*shearU,v=-x*sin+z*cos-dy*shearV;
        double distance=Math.pow(Math.abs(u/ru),exponent)+Math.pow(Math.abs(dy/height),exponent)+Math.pow(Math.abs(v/rv),exponent);
        for (Lobe lobe:lobes) distance=Math.min(distance,lobe.distance(u,dy,v));
        if (form == Form.WEDGE) {
            distance=Math.max(distance,square(Math.max(0,(dy+u*0.30)/(height*0.80))));
            distance=Math.max(distance,square(Math.max(0,(u+v*0.45)/(ru*1.04))));
            distance=Math.max(distance,square(Math.max(0,(-v+u*0.14)/(rv*0.94))));
        }
        if (fracture && dy>height*0.18) {
            double along=u*Math.cos(fractureAngle)+v*Math.sin(fractureAngle);
            double cross=-u*Math.sin(fractureAngle)+v*Math.cos(fractureAngle);
            if (along>fractureOffset && Math.abs(cross)<fractureWidth) distance+=2;
        }
        return distance-noise(x/(r*0.48),(dy+coreY)/(r*0.48),z/(r*0.48))*0.11
                -noise(x*0.63+13,dy*0.63-21,z*0.63+7)*0.02;
    }

    Material materialAt(int x, int y, int z, int columnShift) {
        int dy=y-coreY;
        if (inside(mass,x,dy,z)) {
            long detail=hash(x,y,z);
            if (!layeredBody) return Math.floorMod(detail,9)==0 ? Material.CRYSTAL : Material.FRAGMENT;
            double u=x*cos+z*sin-dy*shearU, v=-x*sin+z*cos-dy*shearV;
            double inner=inner(u,dy,v);
            if (expeditionDeposits) {
                if (inside(crystals,x,dy,z)) return Material.CRYSTAL;
                if (inside(veins,x,dy,z)) return Material.VEIN;
            } else if (inside(veins,x,dy,z)) return inner>0.28 && Math.floorMod(detail,3)==0 ? Material.CRYSTAL : Material.VEIN;
            if (stratified) {
                return switch (structuralLayerAt(x,y,z)) {
                    case CORE_CASING -> Material.CORE_CASING;
                    case LATTICE_MANTLE -> Material.UMBRAL_LATTICE;
                    case OUTER_SHELL -> crackedExterior.get(index(x,dy,z)) ? Material.CRACKED_SHELL : Material.SHELL;
                    case NONE -> throw new IllegalStateException("Missing Umbrafall structural layer inside its body");
                };
            }
            if (inner<0.50) return Math.floorMod(detail,17)==0 ? Material.RESIDUE : Material.CRACKED_SHELL;
            return Math.floorMod(detail,13)==0 ? Material.CRACKED_SHELL : Material.SHELL;
        }
        if (!starterCells.isEmpty()) {
            if (starterCells.contains(new Point(x,y,z))) return Material.RESIDUE;
            // Author the floor as well: natural cave air may sit beneath the starter patch.
            if (starterCells.contains(new Point(x,y+1,z))) return Material.RIM;
        }
        if (!debrisCells.isEmpty() && debrisCells.contains(new Point(x,y,z))) return Material.FRAGMENT;
        return terrainMaterialAt(x,y,z,columnShift);
    }

    private Material terrainMaterialAt(int x,int y,int z,int columnShift) {
        int terrainY=y-columnShift;
        double u=x*cos+z*sin, v=-x*sin+z*cos, azimuth=Math.atan2(v,u);
        double radial=craterRadial(u,v,azimuth);
        if (radial<1) {
            int floor=(int)Math.floor(-depth*(1-Math.pow(radial,bowlPower)));
            if (terrainY>floor) return Material.AIR;
            if (terrainY==floor && !(scatteredSurface && !activeCore)
                    && Math.floorMod(hash(x,floor,z),11)==0) return Material.RESIDUE;
        }
        if (radial>=0.90 && radial<=1.18) {
            double ridge=Math.max(0,1-Math.abs(radial-1.04)/0.14);
            double gap=Math.max(0,(Math.cos(azimuth-rimGapAngle)-0.90)/0.10)*rimGapStrength;
            double bank=(1+bankBias*Math.cos(azimuth))*(1-gap);
            int rimHeight=(int)Math.floor(ridge*Math.max(1,depth*bankHeight)*bank);
            if (terrainY>=-2 && terrainY<=rimHeight) return Material.RIM;
        }
        int spacing=debrisSpacing+(int)Math.round((1-Math.cos(azimuth))*24);
        if (!scatteredSurface && radial>1.15 && x*(long)x+z*(long)z<=radius*(long)radius && terrainY==1
                && Math.floorMod(hash(x,0,z),spacing)==0) {
            return shellDebris ? Material.CRACKED_SHELL : Material.FRAGMENT;
        }
        return Material.KEEP;
    }

    boolean isSurfaceDebris(int x, int y, int z, int columnShift) {
        return y == 1 + columnShift && !inside(mass, x, y-coreY, z);
    }

    double craterRadial(int x, int z) {
        double u=x*cos+z*sin, v=-x*sin+z*cos;
        return craterRadial(u,v,Math.atan2(v,u));
    }

    private double craterRadial(double u, double v, double azimuth) {
        double edge=craterBase+0.03*Math.sin(azimuth*3+craterPhase)+0.02*Math.cos(azimuth*2-craterPhase);
        // The entire rim fits: .76 * 1.10 * 1.18 < 1, without clipping it to a square.
        double craterRadius=radius*Math.clamp(edge,0.61,0.76);
        return Math.sqrt(square(u/craterAlong)+square(v/craterAcross))/craterRadius;
    }

    private double inner(double u, double dy, double v) {
        return square(u/ru)+square(dy/height)+square(v/rv);
    }

    private boolean rawBody(int x, int dy, int z) {
        double u=x*cos+z*sin-dy*shearU, v=-x*sin+z*cos-dy*shearV;
        double inner=inner(u,dy,v);
        // Keep an unbroken central mantle and three-block core cover even on flattened bodies.
        if (inner<=0.64) return true;
        double distance=Math.pow(Math.abs(u/ru),exponent)+Math.pow(Math.abs(dy/height),exponent)+Math.pow(Math.abs(v/rv),exponent);
        for (Lobe lobe:lobes) distance=Math.min(distance,lobe.distance(u,dy,v));
        if (distance>1.16) return false;
        if (form == Form.WEDGE) {
            if (dy+u*0.30>height*0.80 || u+v*0.45>ru*1.04 || -v+u*0.14>rv*0.94) return false;
        }
        if (fracture && dy>height*0.18) {
            double along=u*Math.cos(fractureAngle)+v*Math.sin(fractureAngle);
            double cross=-u*Math.sin(fractureAngle)+v*Math.cos(fractureAngle);
            if (along>fractureOffset && Math.abs(cross)<fractureWidth) return false;
        }
        double coarse=noise(x/(r*0.48),(dy+coreY)/(r*0.48),z/(r*0.48));
        double fine=noise(x*0.63+13,dy*0.63-21,z*0.63+7);
        return distance<=0.98+coarse*0.11+fine*0.02;
    }

    private List<Vein> buildBranches(Random random) {
        if (!layeredBody) return List.of();
        var result=new ArrayList<Vein>();
        int count=4+random.nextInt(5);
        double thickness=Math.max(1.1,r*between(random,0.065,0.085));
        for (int i=0;i<count;i++) {
            double direction=i*Math.PI*2/count+between(random,-0.45,0.45);
            double u=Math.cos(direction)*ru*between(random,0.56,0.76);
            double v=Math.sin(direction)*rv*between(random,0.56,0.76);
            double y=height*between(random,-0.30,0.52);
            double mu=u*0.46+v*0.12, mv=v*0.46-u*0.12, my=y*0.35;
            result.add(new Vein(0,0,0,mu,my,mv,thickness));
            result.add(new Vein(mu,my,mv,u,y,v,thickness));
            result.add(new Vein(mu,my,mv,u*0.62,y+height*0.28,v*0.70,thickness));
        }
        return List.copyOf(result);
    }

    private boolean rawVein(int x,int dy,int z) {
        if (!inside(mass,x,dy,z)) return false;
        double u=x*cos+z*sin-dy*shearU, v=-x*sin+z*cos-dy*shearV;
        if (inner(u,dy,v)>0.64) return false;
        for (Vein vein:branches) if (vein.contains(u,dy,v)) return true;
        return false;
    }

    /** Retain only the core-connected voxels, separately for the body and mineral network. */
    private BitSet connected(CellTest test) {
        int volume=layerSize*(verticalLimit*2+1);
        BitSet result=new BitSet(volume), visited=new BitSet(volume);
        int[] pending=new int[volume];
        int end=visit(index(0,0,0),visited,result,pending,0,test);
        for (int next=0;next<end;next++) {
            int cell=pending[next], x=cell%width, z=cell/width%width, y=cell/layerSize;
            if (x>0) end=visit(cell-1,visited,result,pending,end,test);
            if (x+1<width) end=visit(cell+1,visited,result,pending,end,test);
            if (z>0) end=visit(cell-width,visited,result,pending,end,test);
            if (z+1<width) end=visit(cell+width,visited,result,pending,end,test);
            if (y>0) end=visit(cell-layerSize,visited,result,pending,end,test);
            if (y<verticalLimit*2) end=visit(cell+layerSize,visited,result,pending,end,test);
        }
        return result;
    }

    private int visit(int cell,BitSet visited,BitSet result,int[] pending,int end,CellTest test) {
        if (visited.get(cell)) return end;
        visited.set(cell);
        if (test.contains(cell%width-horizontalLimit,cell/layerSize-verticalLimit,cell/width%width-horizontalLimit)) {
            result.set(cell); pending[end++]=cell;
        }
        return end;
    }
    private int index(int x,int dy,int z) {
        return (dy+verticalLimit)*layerSize+(z+horizontalLimit)*width+x+horizontalLimit;
    }
    private boolean inside(BitSet cells,int x,int dy,int z) {
        return Math.abs(x)<=horizontalLimit && Math.abs(z)<=horizontalLimit && Math.abs(dy)<=verticalLimit
                && cells.get(index(x,dy,z));
    }
    private double noise(double x,double y,double z) {
        int ix=(int)Math.floor(x),iy=(int)Math.floor(y),iz=(int)Math.floor(z);
        double tx=smooth(x-ix),ty=smooth(y-iy),tz=smooth(z-iz),value=0;
        for(int dx=0;dx<=1;dx++) for(int dy=0;dy<=1;dy++) for(int dz=0;dz<=1;dz++) {
            double sample=(hash(ix+dx,iy+dy,iz+dz)>>>11)*0x1.0p-52-1;
            value+=sample*(dx==0?1-tx:tx)*(dy==0?1-ty:ty)*(dz==0?1-tz:tz);
        }
        return value;
    }
    private long hash(int x,int y,int z) {
        return mix(seed ^ x*0x9E3779B97F4A7C15L ^ y*0xC2B2AE3D27D4EB4FL ^ z*0x165667B19E3779F9L);
    }
    private static long mix(long value) {
        value=(value^(value>>>30))*0xBF58476D1CE4E5B9L;
        value=(value^(value>>>27))*0x94D049BB133111EBL;
        return value^(value>>>31);
    }
    private static double square(double value) { return value*value; }
    private static double smooth(double value) { return value*value*(3-2*value); }
    private static double between(Random random,double min,double max) { return min+random.nextDouble()*(max-min); }
}

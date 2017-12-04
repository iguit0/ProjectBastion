package robotbastion;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import robocode.*;
import robocode.util.Utils;
import static robocode.util.Utils.normalRelativeAngleDegrees;

/**
 * Bastion - O nômade
 *
 * Especialista em 1v1. Usa técnicas de IA simples como Wave Surfing
 * para movimentação. E GuessFactor para mira e disparo; que é, 
 * essencialmente, um algoritmo de aprendizagem básico que ajusta
 * o comportamento de disparo com base em dados históricos dos inimigos.
 * É um robô simples, mas decente!
 * 
 * @author Igor 
 * @version 1.0
 * 
 * Código Fonte Livre (sob licença MIT)
 * https://github.com/iguit0/ProjectBastion
 */

public class RobotBastion extends AdvancedRobot {
    // reconhecer cenário
    protected static Rectangle2D.Double _fieldRect;
    // Movimentação e segmentação de velocidade e distância.
    public static int BINS = 47; // padrão: 47 linhas (não mexer!)
    public static double _surfStats[] = new double[BINS];
    public Point2D.Double _myLocation;     // Nossa localização
    public Point2D.Double _enemyLocation;  // localização inimiga
    private static double lateralDirection;
    private static double lastEnemyVelocity;
    // poder de disparo
    private static final double BULLET_POWER = 1.9;

    // armazenar o histórico da movimentação do oponente
    public ArrayList _enemyWaves;
    public ArrayList _surfDirections;
    public ArrayList _surfAbsBearings;
    //energia do oponente
    public static double _oppEnergy = 100.0; 
    
    // espaço entre parede e o robô, usado para não deixar ele bater
    public static double WALL_STICK = 160;
       
    //Armazenar histórico das ondas de disparo
    ArrayList<WaveBullet> waves = new ArrayList<>();
    int[] stats = new int[31];
    int direction = 1; 
    
    @Override
    public void run() {
        // deixando arma e radar livres
        setAdjustGunForRobotTurn(true);
        setAdjustRadarForGunTurn(true);        
        // estilo (cores) do Bastion
        setColors(); 
        // identificando o tamanho do cenário
        _fieldRect = new Rectangle2D.Double(50, 50, 
                getBattleFieldWidth() - 100, getBattleFieldHeight() - 100);
        // padrão de movimentação inimiga (direção, velocidade, etc.)
        _enemyWaves = new ArrayList();
        _surfDirections = new ArrayList();
        _surfAbsBearings = new ArrayList();
        lateralDirection = 1;
        lastEnemyVelocity = 0;        
        
        // loop infinito p/ nunca parar de buscar inimigos
        do{       
            turnRadarRightRadians(Double.POSITIVE_INFINITY);        
        }while(true);
        
    }

    // Esse método é chamado quando um robô vê outro robô
    @Override
    public void onScannedRobot(ScannedRobotEvent e) {
        // Movimentação
        _myLocation = new Point2D.Double(getX(), getY());

        double lateralVelocity = getVelocity() * Math.sin(e.getBearingRadians());
        double absBearing = e.getBearingRadians() + getHeadingRadians();

        setTurnRadarRightRadians(Utils.normalRelativeAngle(absBearing
                - getRadarHeadingRadians()) * 2);

        _surfDirections.add(0,
                new Integer((lateralVelocity >= 0) ? 1 : -1));
        _surfAbsBearings.add(0, new Double(absBearing + Math.PI));

        double bulletPower = _oppEnergy - e.getEnergy();
        if (bulletPower < 3.01 && bulletPower > 0.09
                && _surfDirections.size() > 2) {
            EnemyWave ew = new EnemyWave();
            ew.fireTime = getTime() - 1;
            ew.bulletVelocity = bulletVelocity(bulletPower);
            ew.distanceTraveled = bulletVelocity(bulletPower);
            ew.direction = ((Integer) _surfDirections.get(2)).intValue();
            ew.directAngle = ((Double) _surfAbsBearings.get(2)).doubleValue();
            ew.fireLocation = (Point2D.Double) _enemyLocation.clone(); // last tick

            _enemyWaves.add(ew);
        }

        _oppEnergy = e.getEnergy();

        _enemyLocation = project(_myLocation, absBearing, e.getDistance());

        updateWaves();
        doSurfing();

        // Mira e Disparo        
                // Achar localização inimiga
	double ex = getX() + Math.sin(absBearing) * e.getDistance();
	double ey = getY() + Math.cos(absBearing) * e.getDistance();
 
	// achando padrões de disparo
		for (int i=0; i < waves.size(); i++)
		{
			WaveBullet currentWave = (WaveBullet)waves.get(i);
			if (currentWave.checkHit(ex, ey, getTime()))
			{
				waves.remove(currentWave);
				i--;
			}
		}
 
		double power = Math.min(3, Math.max(.1,.1));
		
		if (e.getVelocity() != 0)
		{
			if (Math.sin(e.getHeadingRadians()-absBearing)*e.getVelocity() < 0)
				direction = -1;
			else
				direction = 1;
		}
		int[] currentStats = stats; // guardando estados visitados
		WaveBullet newWave = new WaveBullet(getX(), getY(), absBearing, power,
                        direction, (int) getTime(), currentStats);
                int bestindex = 15;	
		for (int i=0; i<31; i++)
			if (currentStats[bestindex] < currentStats[i])
				bestindex = i;
 
		// Lógica contrária do WaveBullet
		double guessfactor = (double)(bestindex - (stats.length - 1) / 2)
                        / ((stats.length - 1) / 2);
		double angleOffset = direction * guessfactor * newWave.maxEscapeAngle();
                double gunAdjust = Utils.normalRelativeAngle(
                        absBearing - getGunHeadingRadians() + angleOffset);
               setTurnGunRightRadians(gunAdjust);
               double bearingFromGun = normalRelativeAngleDegrees(absBearing
				- getGunHeading());
               // Quanto mais precisamente objetivo, maior sera a bala.
                // Nao dispare sem precisao, sempre salvar 0,1 de energia
               if (getGunHeat() == 0 && getEnergy() > .2) {
				fire(Math.min(4.5 - Math.abs(bearingFromGun) / 2 - e.getDistance() / 250,
						getEnergy() - .1));
			}              
    }

    public void updateWaves() {
        for (int x = 0; x < _enemyWaves.size(); x++) {
            EnemyWave ew = (EnemyWave) _enemyWaves.get(x);

            ew.distanceTraveled = (getTime() - ew.fireTime) * ew.bulletVelocity;
            if (ew.distanceTraveled
                    > _myLocation.distance(ew.fireLocation) + 50) {
                _enemyWaves.remove(x);
                x--;
            }
        }
    }
    
    // Descobrir onda mais próxima
    public EnemyWave getClosestSurfableWave() {
        double closestDistance = 50000;
        EnemyWave surfWave = null;

        for (int x = 0; x < _enemyWaves.size(); x++) {
            EnemyWave ew = (EnemyWave) _enemyWaves.get(x);
            double distance = _myLocation.distance(ew.fireLocation)
                    - ew.distanceTraveled;

            if (distance > ew.bulletVelocity && distance < closestDistance) {
                surfWave = ew;
                closestDistance = distance;
            }
        }
        return surfWave;
    }

    //Dado o EnemyWave que a bala fora disparada, e o ponto em que nós
    //fomos atingidos, calcular o índice em nossa matriz estatística para esse fator.
    public static int getFactorIndex(EnemyWave ew, Point2D.Double targetLocation) {
        double offsetAngle = (absoluteBearing(ew.fireLocation, targetLocation)
                - ew.directAngle);
        double factor = Utils.normalRelativeAngle(offsetAngle)
                / maxEscapeAngle(ew.bulletVelocity) * ew.direction;

        return (int) limit(0,
                (factor * ((BINS - 1) / 2)) + ((BINS - 1) / 2),
                BINS - 1);
    }

    /* Dado o EnemyWave que a bala fora disparada, e o ponto em que nós
    fomos atingidos, atualize nosso conjunto de estatisticas para refletir
    o perigo nessa área*/
    public void logHit(EnemyWave ew, Point2D.Double targetLocation) {
        int index = getFactorIndex(ew, targetLocation);

        for (int x = 0; x < BINS; x++) {
            // for the spot bin that we were hit on, add 1;
            // for the bins next to it, add 1 / 2;
            // the next one, add 1 / 5; and so on...
            _surfStats[x] += 1.0 / (Math.pow(index - x, 2) + 1);
        }
    }
    
    //se estiver prestes a atingir outro robô, ir pra frente e pra trás.
    @Override
    public void onHitRobot(HitRobotEvent e) {  
        if(e.isMyFault()){
            direction = -direction;
            setBack(10000);
        }
    }  
    
    @Override
    public void onHitByBullet(HitByBulletEvent e) {
        // Se a estrutura _enemyWaves está vazia, nós podemos ter perdido
        // uma detecção nessa onda de alguma maneira
        if (!_enemyWaves.isEmpty()) {
            Point2D.Double hitBulletLocation = new Point2D.Double(
                    e.getBullet().getX(), e.getBullet().getY());
            EnemyWave hitWave = null;

            // Analisando EnemyWaves, e encontre um que poderia nos atingir.
            for (int x = 0; x < _enemyWaves.size(); x++) {
                EnemyWave ew = (EnemyWave) _enemyWaves.get(x);

                if (Math.abs(ew.distanceTraveled
                        - _myLocation.distance(ew.fireLocation)) < 50
                        && Math.abs(bulletVelocity(e.getBullet().getPower())
                                - ew.bulletVelocity) < 0.001) {
                    hitWave = ew;
                    break;
                }
            }

            if (hitWave != null) {
                logHit(hitWave, hitBulletLocation);
                _enemyWaves.remove(_enemyWaves.lastIndexOf(hitWave));
            }
        }
    }
    
    public Point2D.Double predictPosition(EnemyWave surfWave, int direction) {
        Point2D.Double predictedPosition = (Point2D.Double) _myLocation.clone();
        double predictedVelocity = getVelocity();
        double predictedHeading = getHeadingRadians();
        double maxTurning, moveAngle, moveDir;

        int counter = 0; 
        boolean intercepted = false;

        do {
            moveAngle
                    = wallSmoothing(predictedPosition, absoluteBearing(surfWave.fireLocation,
                            predictedPosition) + (direction * (Math.PI / 2)), direction)
                    - predictedHeading;
            moveDir = 1;

            if (Math.cos(moveAngle) < 0) {
                moveAngle += Math.PI;
                moveDir = -1;
            }

            moveAngle = Utils.normalRelativeAngle(moveAngle);

            // Controlar o máximo que podemos girar
            maxTurning = Math.PI / 720d * (40d - 3d * Math.abs(predictedVelocity));
            predictedHeading = Utils.normalRelativeAngle(predictedHeading
                    + limit(-maxTurning, moveAngle, maxTurning));

            // se a Velocidade prevista e moveDir tiverem sinais diferentes
            // que você deseja diminuir caso contrário você deseja acelerar (veja o fator "2")
            predictedVelocity += (predictedVelocity * moveDir < 0 ? 2 * moveDir : moveDir);
            predictedVelocity = limit(-8, predictedVelocity, 8);

            //calcular nova posição prevista
            predictedPosition = project(predictedPosition, predictedHeading, predictedVelocity);

            counter++;

            if (predictedPosition.distance(surfWave.fireLocation)
                    < surfWave.distanceTraveled + (counter * surfWave.bulletVelocity)
                    + surfWave.bulletVelocity) {
                intercepted = true;
            }
        } while (!intercepted && counter < 500);

        return predictedPosition;
    }
    
    // calcular perigo
    public double checkDanger(EnemyWave surfWave, int direction) {
        int index = getFactorIndex(surfWave,
                predictPosition(surfWave, direction));

        return _surfStats[index];
    }
    
    //surfar e escapar dos tiros :)
    public void doSurfing() {
        EnemyWave surfWave = getClosestSurfableWave();

        if (surfWave == null) {
            return;
        }

        double dangerLeft = checkDanger(surfWave, -1);
        double dangerRight = checkDanger(surfWave, 1);

        double goAngle = absoluteBearing(surfWave.fireLocation, _myLocation);
        if (dangerLeft < dangerRight) {
            goAngle = wallSmoothing(_myLocation, goAngle - (Math.PI / 2), -1);
        } else {
            goAngle = wallSmoothing(_myLocation, goAngle + (Math.PI / 2), 1);
        }

        setBackAsFront(this, goAngle);
    }

    //não bater na parede (reforça o outro método)
    public double wallSmoothing(Point2D.Double botLocation, double angle, int orientation) {
        while (!_fieldRect.contains(project(botLocation, angle, WALL_STICK))) {
            angle += orientation * 0.05;
        }
        return angle;
    }

    public static Point2D.Double project(Point2D.Double sourceLocation,
            double angle, double length) {
        return new Point2D.Double(sourceLocation.x + Math.sin(angle) * length,
                sourceLocation.y + Math.cos(angle) * length);
    }
    //Métodos para captar proximidade, distância, energia, ângulos.
    public static double absoluteBearing(Point2D.Double source, Point2D.Double target) {
        return Math.atan2(target.x - source.x, target.y - source.y);
    }

    public static double limit(double min, double value, double max) {
        return Math.max(min, Math.min(value, max));
    }

    public static double bulletVelocity(double power) {
        return (20.0 - (3.0 * power));
    }

    public static double maxEscapeAngle(double velocity) {
        return Math.asin(8.0 / velocity);
    }

    public static void setBackAsFront(AdvancedRobot robot, double goAngle) {
        double angle
                = Utils.normalRelativeAngle(goAngle - robot.getHeadingRadians());
        if (Math.abs(angle) > (Math.PI / 2)) {
            if (angle < 0) {
                robot.setTurnRightRadians(Math.PI + angle);
            } else {
                robot.setTurnLeftRadians(Math.PI - angle);
            }
            robot.setBack(100);
        } else {
            if (angle < 0) {
                robot.setTurnLeftRadians(-1 * angle);
            } else {
                robot.setTurnRightRadians(angle);
            }
            robot.setAhead(100);
        }
    }

    @Override
    public void onPaint(java.awt.Graphics2D g) {
         g.setColor(java.awt.Color.red);
         for(int i = 0; i < _enemyWaves.size(); i++){
            EnemyWave w = (EnemyWave)(_enemyWaves.get(i));
            //Point2D.Double center = w.fireLocation;
            
            //int radius = (int)(w.distanceTraveled + w.bulletVelocity);
            //usar linha acima para ver as ondas no robocode
            int radius = (int)w.distanceTraveled;
 
            Point2D.Double center = w.fireLocation;
            if(radius - 40 < center.distance(_myLocation))
               g.drawOval((int)(center.x - radius ), (int)(center.y - radius), radius*2, radius*2);
         }
    }

    
    public void setColors() {
        // Cores do Bastion :)                
		setBodyColor(Color.black);
		setGunColor(Color.RED);
		setRadarColor(Color.RED);
		setBulletColor(Color.RED);
		setScanColor(Color.RED);
	}
 
}
    // atributos dos oponentes
    class EnemyWave {
        Point2D.Double fireLocation;
        long fireTime;
        double bulletVelocity, directAngle, distanceTraveled;
        int direction;
      
    }
       //atributos para mira e disparo
class WaveBullet{
	private double startX, startY, startBearing, power;
	private long   fireTime;
	private int    direction;
	private int[]  returnSegment;

    public WaveBullet(double startX, double startY, double startBearing, double power, long fireTime, int direction, int[] returnSegment) {
        this.startX = startX;
        this.startY = startY;
        this.startBearing = startBearing;
        this.power = power;
        this.fireTime = fireTime;
        this.direction = direction;
        this.returnSegment = returnSegment;
    }

        public double getBulletSpeed(){
		return 20 - power * 3;
	}
 
	public double maxEscapeAngle(){
		return Math.asin(8 / getBulletSpeed());
	}
        //verificar se foi atingido
        public boolean checkHit(double enemyX, double enemyY, long currentTime){
		//se a distância da onda da origem ao nosso inimigo já passou
                // é distância que a bala teria viajado...
		if (Point2D.distance(startX, startY, enemyX, enemyY) <= 
				(currentTime - fireTime) * getBulletSpeed())
		{
			double desiredDirection = Math.atan2(enemyX - startX, enemyY - startY);
			double angleOffset = Utils.normalRelativeAngle(desiredDirection - startBearing);
			double guessFactor =
				Math.max(-1, Math.min(1, angleOffset / maxEscapeAngle())) * direction;
			int index = (int) Math.round((returnSegment.length - 1) /2 * (guessFactor + 1));
			returnSegment[index]++;
			return true;
		}
		return false;
	}
}
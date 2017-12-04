package Core;

import java.util.Random;
import java.awt.Color;
import static robocode.util.Utils.normalRelativeAngleDegrees;
import robocode.*;


public class Bastion extends AdvancedRobot {
    boolean movingForward; // movimentacao
    boolean inWall; //controle da parede (distancia)

    @Override
    public void run(){

		setColors(); // estilizando robo

		// Cada parte do robo move-se livremente dos outros.
		setAdjustRadarForRobotTurn(true);
		setAdjustGunForRobotTurn(true);
		setAdjustRadarForGunTurn(true);

		// ver se o robo ta mais perto do que 50px da parede
		if (getX() <= 50 || getY() <= 50
				|| getBattleFieldWidth() - getX() <= 50
				|| getBattleFieldHeight() - getY() <= 50) {
			this.inWall = true;
		} else {
			this.inWall = false;
		}
		setAhead(40000); // anda p frente ate inverter o sentido
		setTurnRadarRight(360); // scannear ate encontrar inimigo
		this.movingForward = true; // chamamos setAhead, entao movingForward e verdade

		while (true) {
			// Verifica se estamos perto da parede e se ja verificamos positivo.
			// Caso nao verificamos, inverte a direcao e seta flag p true
			if (getX() > 50 && getY() > 50
					&& getBattleFieldWidth() - getX() > 50
					&& getBattleFieldHeight() - getY() > 50
					&& this.inWall == true) {
				this.inWall = false;
			}
			if (getX() <= 50 || getY() <= 50
					|| getBattleFieldWidth() - getX() <= 50
					|| getBattleFieldHeight() - getY() <= 50) {
				if (this.inWall == false) {
					reverseDirection();
					inWall = true;
				}
			}

			// Se o radar parou de girar, procure um inimigo
			if (getRadarTurnRemaining() == 0.0) {
				setTurnRadarRight(360);
			}

			execute(); // executar todas as acoes
		}
    }


            @Override
	public void onHitWall(HitWallEvent e) {
		reverseDirection();
	}

            @Override
	public void onScannedRobot(ScannedRobotEvent e) {
		// Calcular a posicao exata do robo
		double absoluteBearing = getHeading() + e.getBearing();

		// vire so o necessario e nunca mais do que uma volta...
		// vendo-se o angulo que fazemos com o robo alvo e descontando
		// o Heading e o Heading do Radar pra ficar com o angulo
		// correto, normalmente.
		double bearingFromGun = normalRelativeAngleDegrees(absoluteBearing
				- getGunHeading());
		double bearingFromRadar = normalRelativeAngleDegrees(absoluteBearing
				- getRadarHeading());

		// giro para realizar movimento espiral ao inimigo
		// (90 levaria ao paralelo)
		if (this.movingForward) {
			setTurnRight(normalRelativeAngleDegrees(e.getBearing() + 80));
		} else {
			setTurnRight(normalRelativeAngleDegrees(e.getBearing() + 100));
		}

		// Se perto o suficiente, atirar!
		if (Math.abs(bearingFromGun) <= 4) {
			setTurnGunRight(bearingFromGun); // mantem o canhao centrado sobre o inimigo
			setTurnRadarRight(bearingFromRadar); // mantem o radar centrado sobre o inimigo

			// Quanto mais precisamente objetivo, maior sera a bala.
			// Nao dispare sem precisao, sempre salvar 0,1 de energia
			if (getGunHeat() == 0 && getEnergy() > .2) {
				fire(Math.min(
						4.5 - Math.abs(bearingFromGun) / 2 - e.getDistance() / 250,
						getEnergy() - .1));
			}
		} // caso contrario, basta definir a arma para virar.
		else {
			setTurnGunRight(bearingFromGun);
			setTurnRadarRight(bearingFromRadar);
		}

		// se o radar nao estiver girando, gera evento de giro (scanner)
		if (bearingFromGun == 0) {
			scan();
		}
	}

	// em contato com o robo, se tenha sido por nossa conta, inverte a direcao
            @Override
	public void onHitRobot(HitRobotEvent e) {
		if (e.isMyFault()) {
			reverseDirection();
		}
	}

	private void setColors() {
		setBodyColor(Color.MAGENTA);
		setGunColor(Color.ORANGE);
		setRadarColor(Color.ORANGE);
		setBulletColor(Color.green);
		setScanColor(Color.YELLOW);
	}

	// mudar de frente para tras e vice-versa
	public void reverseDirection() {
		if (this.movingForward) {
			setBack(40000);
			this.movingForward = false;
		} else {
			setAhead(40000);
			this.movingForward = true;
		}
	}
}

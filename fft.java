import java.awt.Color;
import java.awt.Graphics;
import java.nio.ByteBuffer;

import javax.sound.sampled.AudioFormat;
import javax.swing.JPanel;

//Yay for JTransforms - the fastest Java FFT so far :)
import edu.emory.mathcs.jtransforms.fft.DoubleFFT_1D;

@SuppressWarnings("serial")
public class fft extends JPanel implements jsdr.JsdrTab {
	public final String CFG_FFTHAM = "fft-hamming";
	public final String CFG_FFTLOG = "fft-log";
	public final String CFG_FFTGAIN= "fft-gain";
	public final String CFG_FFTAUTO= "fft-auto";

	private jsdr parent;
	private double[] dat;
	private double[] spc;
	private double[] psd;
	private double[] win;
	private boolean dow;
	private boolean log;
	private boolean auto;
	private int gain;
	private AudioFormat fmt;

	public fft(jsdr p, AudioFormat af, int bufsize) {
		parent = p;
		// Allocate buffers according to format..
		int sbytes = (af.getSampleSizeInBits()+7)/8;
		dat = new double[bufsize/sbytes/af.getChannels()*2];
		spc = new double[dat.length];
		psd = new double[dat.length/2+1];	// add one for spectal maxima
		win = new double[dat.length/2];
		// Calculate window coefficients (hamming)
		for (int s=0; s<win.length; s++)
			win[s] = (double) (0.54 - 0.46*Math.cos(2*Math.PI*s/win.length));
		fmt = af;
		// Reg hot keys
		p.regHotKey('h', "Toggle Hamming window");
		p.regHotKey('l', "Toggle log scale");
		p.regHotKey('g', "Increase fft gain");
		p.regHotKey('G', "Decrease fft gain");
		p.regHotKey('A', "Toggle fft gain auto");
		// Grab saved config
		dow = jsdr.getIntConfig(CFG_FFTHAM, 1)!=0 ? true : false;
		log = jsdr.getIntConfig(CFG_FFTLOG, 1)!=0 ? true : false;
		auto = jsdr.getIntConfig(CFG_FFTAUTO, 1)!=0 ? true : false;
		gain = jsdr.getIntConfig(CFG_FFTGAIN, 0);
	}

	protected void paintComponent(Graphics g) {
		// Clear to black
		g.setColor(Color.BLACK);
		g.fillRect(0, 0, getWidth(), getHeight());
		// Step size and FFT offset for resampling to screen size
		float s = (float)(dat.length/2)/(float)getWidth();
		int off = getWidth()/2;
		// adjust step and offset if only single channel data
		if (fmt.getChannels()<2) {
			s = s/2;
			off = 0;
		}
		int t = (int)Math.ceil(s);
		g.setColor(Color.DARK_GRAY);
		if (dow)
			g.drawString("step(win): "+s+"/"+t, 2, 24);
		else
			g.drawString("step(raw): "+s+"/"+t, 2, 24);
		// Scale factor to fit -1 to 1 float sample data into 1/3 screen height
		float h = (float)(getHeight()/6);
		// Offset down screen
		int o = getHeight()/6;
		// I in top 1/3rd
		g.setColor(Color.RED);
		g.drawString("I: "+parent.ic, 2, 12);
		int ly = 0;
		for (int p=0; p<getWidth()-1; p++) {
			int y = (int)(getMax(dat, 2*(int)(p*s), t)*h);
			g.drawLine(p, ly+o, p+1, y+o);
			ly = y;
		}
		// Q in middle 1/3rd
		g.setColor(Color.BLUE);
		g.drawString("Q: "+parent.qc, 2, getHeight()/3+12);
		o = getHeight()/2;
		ly = 0;
		for (int p=0; p<getWidth()-1; p++) {
			int y = (int)(getMax(dat, 2*(int)(p*s)+1, t)*h);
			g.drawLine(p, ly+o, p+1, y+o);
			ly = y;
		}
		// PSD and demod filter in lower 1/3rd (log scale if selected)
		int flo = jsdr.getIntPublish("demod-filter-low", Integer.MIN_VALUE);
		int fhi = jsdr.getIntPublish("demod-filter-high", Integer.MAX_VALUE);
		if (flo > Integer.MIN_VALUE && fhi < Integer.MAX_VALUE) {
			g.setColor(Color.decode("0x0f0f0f"));
			int wd = (fmt.getChannels()<2) ? getWidth() : getWidth()/2;
			int ts = (int)((float)flo/(fmt.getSampleRate()/2)*(float)wd)+off;
			int tw = (int)(((float)fhi-flo)/(fmt.getSampleRate()/2)*(float)wd);
			g.fillRect(ts, getHeight()*2/3, tw, getHeight());
		}
		g.setColor(Color.GREEN);
		double pmax = psd[psd.length-1];
		if (log)
			g.drawString("PSD(log): "+Math.log10(pmax+1.0), 2, getHeight()*2/3+12);
		else
			g.drawString("PSD(raw): "+pmax, 2, getHeight()*2/3+12);
		o = getHeight();
		h = (float)(auto ? (log ? (getHeight()/3)/Math.log10(pmax+1.0) : (getHeight()/3)/pmax) : gain);
		g.drawString("gain("+(auto?'A':'-')+"):"+h, getWidth()-100, getHeight()*2/3+12);
		ly = 0;
		for (int p=0; p<getWidth()-1; p++) {
			// offset and wrap index to display negative freqs, then positives..
			int i = (p+off) % getWidth();
			int y = log ? (int)(Math.log10(getMax(psd, (int)(i*s), t/2)+1.0)*h) : (int)(getMax(psd, (int)(i*s), t/2)*h);
			g.drawLine(p, o-ly, p+1, o-y);
			ly = y;
//					if (2*(int)(p*s)<=spos && spos<=2*(int)((p+1)*s)) {
//						g.drawString("Max", p, o-y-2);
//					}
		}
		// BPSK tuning bar(s) (if available)
		boolean dbar = true;
		g.setColor(Color.CYAN);
		for (int fc=0; dbar; fc++) {
			dbar = false;
			String nm = "FUNcube"+fc+"-bpsk-centre";
			int cb = jsdr.getIntPublish(nm, -1);
			if (cb>0) {
				int tc = (int)((float)cb/s)+off;
				g.drawLine(tc, getHeight(), tc, getHeight()*2/3);
				g.drawString(nm+":"+cb, tc+5, getHeight()*5/6);
				dbar = true;
			}
			nm = "FUNcube"+fc+"-bpsk-tune";
			cb = jsdr.getIntPublish(nm, -1);
			if (cb>0) {
				int wd = (fmt.getChannels()<2) ? getWidth() : getWidth()/2;
				int tc = (int)((float)cb/(fmt.getSampleRate()/2)*(float)wd)+off;
				g.drawLine(tc, getHeight(), tc, getHeight()*2/3);
				g.drawString(nm+":"+cb, tc+5, getHeight()*5/6);
				dbar = true;
			}
		}
		// Reticle
		g.setColor(Color.DARK_GRAY);
		g.drawLine(0, getHeight()/3, getWidth(), getHeight()/3);
		g.drawLine(0, getHeight()*2/3, getWidth(), getHeight()*2/3);
		int my1 = getHeight()*2/3-2;
		int my2 = getHeight()*2/3+2;
		int mxs = getWidth()/20;
		boolean abv = true;
		for (int x=0; x<getWidth()-mxs; x+=mxs) {
			g.drawLine(x, my1, x, my2);
			double _mf;
			if (fmt.getChannels()<2) {
				_mf = fmt.getSampleRate()/2.0 * (double)x / (double)getWidth();
			} else {
				_mf = (fmt.getSampleRate() * (double)x / (double)getWidth()) - fmt.getSampleRate()/2.0;
			}
			g.drawString((int)_mf + "Hz", x, abv ? getHeight()*2/3-2 : getHeight()*2/3+12);
			abv = !abv;
		}
	}
	// Find largest magnitude value in a array from offset o, length l, stride 2
	private double getMax(double[]a, int o, int l) {
		double r = 0;
		for (int i=o; i<o+l; i+=2) {
			if (Math.abs(a[i])>r)
				r=a[i];
		}
		return r;
	}

	public void newBuffer(ByteBuffer buf) {
		// Convert to array of floats (scaled -1 to 1).. and apply windowing function if required
		int div = 2<<(fmt.getSampleSizeInBits()-1);
		for (int s=0; s<dat.length; s+=2) {
			dat[s] = ((double)(buf.getShort()+parent.ic) / (double)div) * (dow ? win[s/2] : 1);
			if (fmt.getChannels()>1)
				dat[s+1] = ((double)(buf.getShort()+parent.qc) / (double)div) * (dow ? win[s/2] : 1);
			else
				dat[s+1] = 0;
		}
		// Copy to preserve original input
		System.arraycopy(dat, 0, spc, 0, dat.length);
		// FFT
		DoubleFFT_1D fft = new DoubleFFT_1D(spc.length/2);
		fft.complexForward(spc);
		// Calculate power spectral density (PSD)
		double m = 0;
		int p = -1;
		for (int s=0; s<spc.length-1; s+=2) {
			psd[s/2] = Math.sqrt((spc[s]*spc[s]) + (spc[s+1]*spc[s+1]));	// Compute PSD
			if (m<psd[s/2]) {
				m=psd[s/2];
				p = s;
			}
		}
		// Stash maxima for scaling display
		psd[psd.length-1] = m;
		// Upcall for scanner
		if (fmt.getChannels()<2)		// convert array index to frequency offset
			p = (p*(int)fmt.getSampleRate())/(2*dat.length);
		else
			p = (p*(int)fmt.getSampleRate())/dat.length - (int)fmt.getSampleRate()/2;
		parent.spectralMaxima((float)m, p);
		// Skip redraw unless we are visible
		if (isVisible())
			repaint();
	}

	public void hotKey(char c) {
		if ('h'==c)
			dow = !dow;
		if ('l'==c)
			log = !log;
		if ('A'==c)
			auto = !auto;
		if ('g'==c)
			gain++;
		if ('G'==c)
			gain--;
		jsdr.config.setProperty(CFG_FFTHAM, dow ? "1" : "0");
		jsdr.config.setProperty(CFG_FFTLOG, log ? "1" : "0");
		jsdr.config.setProperty(CFG_FFTAUTO, auto ? "1" : "0");
		jsdr.config.setProperty(CFG_FFTGAIN, String.valueOf(gain));
	}
}
